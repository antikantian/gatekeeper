package co.quine.gatekeeper.actors

import akka.actor._
import argonaut._, Argonaut._
import scalaj.http._

import scala.concurrent.duration._

import co.quine.gatekeeper.Codec._
import co.quine.gatekeeper.config.Config.TwitterConfig._
import co.quine.gatekeeper.tokens._

object RateLimitActor {
  case class RateLimitStatus(rate_limit_context: RateLimitContext, resources: Resources)
  case class RateLimitContext(access_token: String)
  case class Resources(
      application: AppEndpoint,
      users: UsersEndpoint,
      statuses: StatusesEndpoint,
      friends: FriendsEndpoint,
      followers: FollowersEndpoint)
  case class AppEndpoint(rate_limit_status: Stats)
  case class UsersEndpoint(lookup: Stats, show: Stats)
  case class StatusesEndpoint(lookup: Stats, show: Stats, user_timeline: Stats)
  case class FriendsEndpoint(ids: Stats, list: Stats)
  case class FollowersEndpoint(ids: Stats, list: Stats)
  case class Stats(limit: Int, remaining: Int, reset: Long)

  implicit def RateLimitStatusDecodeJson: DecodeJson[RateLimitStatus] = {
    DecodeJson(c => for {
      rate_limit_context <- (c --\ "rate_limit_context").as[RateLimitContext]
      resources <- (c --\ "resources").as[Resources]
    } yield RateLimitStatus(rate_limit_context, resources))
  }

  implicit def RateLimitContextDecodeJson: DecodeJson[RateLimitContext] = {
    DecodeJson(c => for {
      access_token <- (c --\ "access_token").as[Option[String]]
      application <- (c --\ "application").as[Option[String]]
    } yield RateLimitContext(access_token.getOrElse(application.getOrElse("None"))))
  }

  implicit def ResourcesDecodeJson: DecodeJson[Resources] = {
    DecodeJson(c => for {
      application <- (c --\ "application").as[AppEndpoint]
      users <- (c --\ "users").as[UsersEndpoint]
      statuses <- (c --\ "statuses").as[StatusesEndpoint]
      friends <- (c --\ "friends").as[FriendsEndpoint]
      followers <- (c --\ "followers").as[FollowersEndpoint]
    } yield Resources(application, users, statuses, friends, followers))
  }

  implicit def AppEndpointDecodeJson: DecodeJson[AppEndpoint] = {
    DecodeJson(c => for {
      rate_limit_status <- (c --\ "/application/rate_limit_status").as[Stats]
    } yield AppEndpoint(rate_limit_status))
  }

  implicit def UsersEndpointDecodeJson: DecodeJson[UsersEndpoint] = {
    DecodeJson(c => for {
      lookup <- (c --\ "/users/lookup").as[Stats]
      show <- (c --\ "/users/show/:id").as[Stats]
    } yield UsersEndpoint(lookup, show))
  }

  implicit def StatusesEndpointDecodeJson: DecodeJson[StatusesEndpoint] = {
    DecodeJson(c => for {
      lookup <- (c --\ "/statuses/lookup").as[Stats]
      show <- (c --\ "/statuses/show/:id").as[Stats]
      user_timeline <- (c --\ "/statuses/user_timeline").as[Stats]
    } yield StatusesEndpoint(lookup, show, user_timeline))
  }

  implicit def FriendsEndpointDecodeJson: DecodeJson[FriendsEndpoint] = {
    DecodeJson(c => for {
      ids <- (c --\ "/friends/ids").as[Stats]
      list <- (c --\ "/friends/list").as[Stats]
    } yield FriendsEndpoint(ids, list))
  }

  implicit def FollowersEndpointDecodeJson: DecodeJson[FollowersEndpoint] = {
    DecodeJson(c => for {
      ids <- (c --\ "/followers/ids").as[Stats]
      list <- (c --\ "/followers/list").as[Stats]
    } yield FollowersEndpoint(ids, list))
  }

  implicit def StatsDecodeJson: DecodeJson[Stats] = {
    DecodeJson(c => for {
      limit <- (c --\ "limit").as[Int]
      remaining <- (c --\ "remaining").as[Int]
      reset <- (c --\ "reset").as[Long]
    } yield Stats(limit, remaining, reset))
  }

  def props(tokens: Seq[ResourceToken], consumer: ConsumerToken): Props = Props(new RateLimitActor(tokens, consumer))

}

class RateLimitActor(tokens: Seq[ResourceToken], consumer: ConsumerToken) extends Actor with ActorLogging {

  import RateLimitActor._
  import context.dispatcher

  val updateSchedule = context.system.scheduler.schedule(1.minute, 5.minutes, self, "update")

  val rateLimitUri = s"$twitterScheme://$twitterHost/$twitterVersion/application/rate_limit_status.json"

  def rateLimitRequest(bearer: String): Option[RateLimitStatus] = {
    val request = Http(rateLimitUri).header("Authorization", s"Bearer $bearer").asString
    parseRateLimitResponse(request)
  }

  def rateLimitRequest(token: AccessToken): Option[RateLimitStatus] = {
    val consumer_token = Token(consumer.key, consumer.secret)
    val access_token = Token(token.key, token.secret)
    val request = Http(rateLimitUri).oauth(consumer_token, access_token).asString
    parseRateLimitResponse(request)
  }

  def parseRateLimitResponse(r: HttpResponse[String]) = Parse.decodeOption[RateLimitStatus](r.body)

  def runUpdate(): Unit = {
    log.info("Running ratelimit update")

    var requestCount = 0
    var updateCount = 0

    val rateLimitUpdates: Iterable[RateLimitStatus] = tokens.groupBy(_.key).map(_._2.head).flatMap { t =>
      t.credential match {
        case AccessToken(a, b) => requestCount += 1; rateLimitRequest(AccessToken(a, b))
        case BearerToken(a) => requestCount += 1; rateLimitRequest(a)
      }
    }

    rateLimitUpdates foreach { update =>
      tokens filter { resource =>
        resource.key == update.rate_limit_context.access_token } foreach { r =>
          r.update(update)
          updateCount += 1
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