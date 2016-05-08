package co.quine.gatekeeper.actors

import akka.actor._
import argonaut._, Argonaut._
import scalaj.http._

import scala.concurrent.duration._

import co.quine.gatekeeper.Codec._
import co.quine.gatekeeper.config.Config.TwitterConfig._

object RateLimitActor {
  case class RateLimitArray(token: String, resources: Seq[RateLimitEndpoint])
  case class RateLimitEndpoint(resource: TwitterResource, stats: Stats)
  case class Stats(limit: Int, remaining: Int, reset: Long)

  implicit def RateLimitArrayDecodeJson: DecodeJson[RateLimitArray] = {
    DecodeJson(c => for {
      token <- (c --\ "rate_limit_context" =\ 1).as[String]
      usersLookup <- (c --\ "resources" --\ "users" --\ "/users/lookup").as[Stats]
      usersShow <- (c --\ "resources" --\ "users" --\ "/users/show/:id").as[Stats]
      sLookup <- (c --\ "resources" --\ "statuses" --\  "/statuses/lookup").as[Stats]
      sShow <- (c --\ "resources" --\ "statuses" --\ "/statuses/show/:id").as[Stats]
      friendsIds <- (c --\ "resources" --\ "friends" --\ "/friends/ids").as[Stats]
      friendsList <- (c --\ "resources" --\ "friends" --\ "/friends/list").as[Stats]
      followersIds <- (c --\ "resources" --\ "followers" --\ "/followers/ids").as[Stats]
      followersList <- (c --\ "resources" --\ "followers" --\ "/followers/list").as[Stats]
      } yield RateLimitArray(token,
                             Seq(RateLimitEndpoint(UsersLookup, usersLookup),
                                 RateLimitEndpoint(UsersShow, usersShow),
                                 RateLimitEndpoint(StatusesLookup, sLookup),
                                 RateLimitEndpoint(StatusesShow, sShow),
                                 RateLimitEndpoint(FriendsIds, friendsIds),
                                 RateLimitEndpoint(FriendsList, friendsList),
                                 RateLimitEndpoint(FollowersIds, followersIds),
                                 RateLimitEndpoint(FollowersList, followersList))))
    }

  implicit def StatsDecodeJson: DecodeJson[Stats] = {
    DecodeJson(c => for {
      limit <- (c --\ "limit").as[Int]
      remaining <- (c --\ "remaining").as[Int]
      reset <- (c --\ "reset").as[Long]
    } yield Stats(limit, remaining, reset))
  }

  def props(tokens: TokenBook): Props = Props(new RateLimitActor(tokens))

}

class RateLimitActor(tokens: TokenBook) extends Actor with ActorLogging {

  import co.quine.gatekeeper.Codec._
  import RateLimitActor._

  implicit val ec = context.dispatcher

  val updateSchedule = context.system.scheduler.schedule(1.minute, 5.minutes, self, "update")

  val rateLimitUri = s"$twitterScheme://$twitterHost/$twitterVersion/application/rate_limit_status.json"

  def rateLimitRequest(bearer: BearerToken): Seq[RateLimit] = {
    val request = Http(rateLimitUri).header("Authorization", s"Bearer ${bearer.token}").asString
    parseRateLimitResponse(request)
  }

  def rateLimitRequest(token: AccessToken): Seq[RateLimit] = {
    val consumer_token = Token(tokens.consumer.key, tokens.consumer.secret)
    val access_token = Token(token.key, token.secret)
    val request = Http(rateLimitUri).oauth(consumer_token, access_token).asString
    parseRateLimitResponse(request)
  }

  def parseRateLimitResponse(r: HttpResponse[String]) = Parse.decodeOption[RateLimitArray](r.body) match {
    case Some(update) =>
      val token = tokens.findByKey(update.token) match { case Some(x) => x }
      update.resources.map(e => RateLimit(token, e.resource, e.stats.remaining, e.stats.reset))
  }

  def runUpdate(): Unit = {
    log.info("Running ratelimit update")

    var requestCount = 0
    var updateCount = 0

    tokens.all foreach {
      case token: AccessToken =>
        requestCount += 1
        rateLimitRequest(token) foreach { r =>
          updateCount += 1
          context.parent ! r
        }
      case token: BearerToken =>
        requestCount += 1
        rateLimitRequest(token) foreach { r =>
          updateCount += 1
          context.parent ! r
        }
    }
    log.info(s"Ratelimit update complete: $requestCount requests, $updateCount updates")
  }

  override def preStart() = log.info(s"${self.path.name}: up")

  override def postStop() = updateSchedule.cancel()

  def receive = {
    case "update" => runUpdate()
  }

}