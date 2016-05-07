package co.quine.gatekeeper.actors

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import argonaut._
import scalaj.http._
import scala.collection.mutable
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Success, Failure}

import co.quine.gatekeeper._
import co.quine.gatekeeper.config.Config._
import co.quine.gatekeeper.connectors._

object GatekeeperActor {

  def props(librarian: ActorRef) = Props(new GatekeeperActor(librarian))
}

class GatekeeperActor(librarian: ActorRef)
  extends EndpointManager
    with RedisConnector
    with Actor
    with ActorLogging {

  import Codec._

  var tokens: TokenBook = _

  val endpointArray = mutable.ArrayBuffer[EndpointCard]()

  override def preStart() = {
    val tokenFutures = for {
      redisAccess <- rc.smembers[AccessToken]("twitter:access_tokens")
      redisConsumer <- rc.get[ConsumerToken]("twitter:consumer_token")
      twitterBearer <- redisConsumer match { case Some(consumer) => obtainBearerToken(consumer) }
    } yield TokenBook(redisAccess, redisConsumer.getOrElse(throw new Exception("Consumer not found")), twitterBearer)

    tokenFutures onSuccess {
      case t@TokenBook(access: Seq[AccessToken], consumer: ConsumerToken, bearer: BearerToken) => tokens = t
    }
  }

  def receive = clients orElse endpoints


  def clients: Receive = {
    case r: Request => onRequest(r, sender)
    case r: RateLimit => lookupEndpoint(r.resource) ! r
  }

  def endpoints: Receive = {

  }

  def onRequest(r: Request, origin: ActorRef) = r match {
    case TokenRequest(uuid, resource) =>
      val endpoint = lookupEndpoint(resource)
      implicit val ec = context.dispatcher
      endpoint.ask(NeedToken)(5.seconds) andThen {
        case Success(x: Token) => origin ! TokenResponse(uuid, x)
        case Failure(_) => origin ! ErrorResponse(uuid, ResourceDown)
      }
    case ConsumerRequest(uuid, _) => origin ! TokenResponse(uuid, tokens.consumer)
  }

  def onUpdate(u: Update): Unit = u match {
    case RateLimit(token, resource, remaining, ttl) =>
  }

  private def createEndpoint(resource: TwitterResource): Unit = {
    val endpointActor = context.actorOf(EndpointActor.props(resource, tokens))
    endpointArray.append(EndpointCard(resource, endpointActor))
  }

  private def lookupEndpoint(r: Resource): ActorRef = {
    endpointArray.find(e => e.resource == r) match {
      case Some(endpoint) => endpoint.address
      case _ =>
    }
  }

  private def invalidateBearer: HttpResponse[String] = {
    Http("https://api.twitter.com/oauth2/invalidate_token")
      .auth(tokens.consumer.key, tokens.consumer.secret)
      .header("User-Agent", s"Gatekeeper v$version")
      .header("Content-Type", "application/x-www-form-urlencoded")
      .postForm(Seq(("access_token", tokens.bearer.token)))
      .asString
  }

  private def obtainBearerToken(consumer: ConsumerToken): Future[BearerToken] = Future {
    val response = Http("https://api.twitter.com/oauth2/token")
      .header("User-Agent", s"Gatekeeper v$version")
      .auth(consumer.key, consumer.secret)
      .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
      .postForm(Seq(("grant_type", "client_credentials")))
      .asString

    Parse.decodeOption[BearerToken](response.body).getOrElse(BearerToken("Unavailable"))
  }
}