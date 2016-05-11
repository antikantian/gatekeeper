package co.quine.gatekeeper.actors

import akka.actor._
import argonaut._, Argonaut._
import scalaj.http._

import scala.concurrent.duration._

import co.quine.gatekeeper._
import co.quine.gatekeeper.config.Config.TwitterConfig._

object RateLimitActor {

  import Codec._

  case class RateLimitArray(resources: Seq[RateLimitEndpoint])
  case class RateLimitEndpoint(resource: TwitterResource, stats: Stats)
  case class Stats(limit: Int, remaining: Int, reset: Long)

  implicit def RateLimitArrayDecodeJson: DecodeJson[RateLimitArray] = {
    DecodeJson(c => for {
      usersLookup <- (c --\ "resources" --\ "users" --\ "/users/lookup").as[Stats]
      usersShow <- (c --\ "resources" --\ "users" --\ "/users/show/:id").as[Stats]
      sLookup <- (c --\ "resources" --\ "statuses" --\  "/statuses/lookup").as[Stats]
      sShow <- (c --\ "resources" --\ "statuses" --\ "/statuses/show/:id").as[Stats]
      sUserTimeline <- (c --\ "resources" --\ "statuses" --\ "/statuses/user_timeline").as[Stats]
      friendsIds <- (c --\ "resources" --\ "friends" --\ "/friends/ids").as[Stats]
      friendsList <- (c --\ "resources" --\ "friends" --\ "/friends/list").as[Stats]
      followersIds <- (c --\ "resources" --\ "followers" --\ "/followers/ids").as[Stats]
      followersList <- (c --\ "resources" --\ "followers" --\ "/followers/list").as[Stats]
      } yield RateLimitArray(
                             Seq(RateLimitEndpoint(UsersLookup, usersLookup),
                                 RateLimitEndpoint(UsersShow, usersShow),
                                 RateLimitEndpoint(StatusesLookup, sLookup),
                                 RateLimitEndpoint(StatusesShow, sShow),
                                 RateLimitEndpoint(StatusesUserTimeline, sUserTimeline),
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

  import Codec._
  import RateLimitActor._

  implicit val ec = context.dispatcher

  val updateSchedule = context.system.scheduler.schedule(1.minute, 5.minutes, self, "update")

  val rateLimitUri = s"$twitterScheme://$twitterHost/$twitterVersion/application/rate_limit_status.json"

  def rateLimitRequest(bearer: BearerToken): Option[Seq[RateLimit]] = {
    val request = Http(rateLimitUri).header("Authorization", s"Bearer ${bearer.key}").asString
    parseRateLimitResponse(request) map { update =>
      update.resources.map(e => RateLimit(e.resource, bearer.key, e.stats.remaining, e.stats.reset))
    }
  }

  def rateLimitRequest(token: AccessToken): Option[Seq[RateLimit]] = {
    val consumer_token = Token(tokens.consumer.key, tokens.consumer.secret)
    val access_token = Token(token.key, token.secret)
    val request = Http(rateLimitUri).oauth(consumer_token, access_token).asString
    parseRateLimitResponse(request) map { update =>
      update.resources.map(e => RateLimit(e.resource, token.key, e.stats.remaining, e.stats.reset))
    }
  }

  def parseRateLimitResponse(r: HttpResponse[String]) = Parse.decodeOption[RateLimitArray](r.body)

  def runUpdate(): Unit = {
    log.info("Running ratelimit update")

    var requestCount = 0
    var updateCount = 0

    tokens.all foreach {
      case token: AccessToken =>
        requestCount += 1
        rateLimitRequest(token) foreach { r =>
          r foreach { update =>
            updateCount += 1
            context.parent ! update
          }
        }
      case token: BearerToken =>
        requestCount += 1
        rateLimitRequest(token) foreach { r =>
          r foreach { update =>
            updateCount += 1
            context.parent ! update
          }
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