package co.quine.gatekeeper.actors

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import argonaut._
import redis._
import scalaj.http._
import scala.collection.mutable
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Success, Failure}

import co.quine.gatekeeper._
import co.quine.gatekeeper.config.Config._

object GatekeeperActor {

  import Codec._

  case object Endpoints {
    val all = Seq(UsersLookup, UsersShow, StatusesLookup, StatusesShow, StatusesUserTimeline,
      FriendsIds, FriendsList, FollowersIds, FollowersList)
  }

  def props = Props(new GatekeeperActor)
}

class GatekeeperActor extends Actor with ActorLogging {

  import Codec._
  import GatekeeperActor._

  implicit val system = context.system
  implicit val ec = context.dispatcher
  implicit val timeout = Timeout(60.seconds)

  var tokens: TokenBook = _
  var rateLimitActor: ActorRef = _

  val endpointArray = mutable.ArrayBuffer[EndpointCard]()

  override def preStart() = {
    val rc = new RedisClient(host = BackendConfig.redisHost)
    val tokenFutures = for {
      redisAccess <- rc.smembers[AccessToken]("twitter:access_tokens")
      redisConsumer <- rc.get[ConsumerToken]("twitter:consumer_token")
      twitterBearer <- redisConsumer match { case Some(consumer) => obtainBearerToken(consumer) }
    } yield TokenBook(redisAccess, redisConsumer.getOrElse(throw new Exception("Consumer not found")), twitterBearer)

    tokenFutures onSuccess {
      case t@TokenBook(access: Seq[AccessToken], consumer: ConsumerToken, bearer: BearerToken) =>
        tokens = t
        rateLimitActor = context.actorOf(RateLimitActor.props(t), "rate-limit")
        Endpoints.all.foreach(resource => createEndpoint(resource))
    }
    log.info(s"${self.path.name}: Ready")
  }

  def receive = commands

  def commands: Receive = {
    case Consumer => sender ! tokens.consumer
    case NewBearer => refreshBearerToken(sender())
    case cmd@Grant(resource) => lookupEndpoint(resource).foreach(epc => epc.address forward cmd)
    case cmd@Remaining(resource) => lookupEndpoint(resource).foreach(epc => epc.address forward cmd)
    case cmd@TTL(resource) => lookupEndpoint(resource).foreach(epc => epc.address forward cmd)
    case cmd@RateLimit(resource, tokenKey, remaining, reset) =>
      lookupEndpoint(resource).foreach(epc => epc.address forward cmd)
  }

  private def createEndpoint(resource: TwitterResource): Unit = {
    val endpointActor = context.actorOf(EndpointActor.props(resource, tokens))
    endpointArray.append(EndpointCard(resource, endpointActor))
  }

  private def distributeBearerToken(b: BearerToken): Unit = endpointArray.foreach(_.address ! NewBearerToken(b))

  private def lookupEndpoint(r: Resource): Option[EndpointCard] = endpointArray.find(e => e.resource == r)

  private def invalidateBearerToken: Boolean = {
    Http("https://api.twitter.com/oauth2/invalidate_token")
      .auth(tokens.consumer.key, tokens.consumer.secret)
      .header("User-Agent", s"Gatekeeper v$version")
      .header("Content-Type", "application/x-www-form-urlencoded")
      .postForm(Seq(("access_token", tokens.bearer.key)))
      .asString
      .is2xx
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

  private def refreshBearerToken(replyTo: ActorRef): Unit = {
    invalidateBearerToken match {
      case true => obtainBearerToken(tokens.consumer) onSuccess {
        case token@BearerToken(b) =>
          distributeBearerToken(token)
          replyTo ! token
      }
    }
  }
}