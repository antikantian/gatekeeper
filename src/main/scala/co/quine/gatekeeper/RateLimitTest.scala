package co.quine.gatekeeper

import akka.actor._
import argonaut._
import Argonaut._

import scalaj.http._
import scala.concurrent.duration._
import co.quine.gatekeeper.actors._
import co.quine.gatekeeper.config.Config._
import co.quine.gatekeeper.config.Config.TwitterConfig._
import redis.RedisClient

import scala.concurrent.Future

object RateLimitTest {

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
      friendsIds <- (c --\ "resources" --\ "friends" --\ "/friends/ids").as[Stats]
      friendsList <- (c --\ "resources" --\ "friends" --\ "/friends/list").as[Stats]
      followersIds <- (c --\ "resources" --\ "followers" --\ "/followers/ids").as[Stats]
      followersList <- (c --\ "resources" --\ "followers" --\ "/followers/list").as[Stats]
    } yield RateLimitArray(
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
}

class RateLimitTest() {

  import Codec._
  import RateLimitTest._

  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher

  var tokens: TokenBook = _

  val rc = new RedisClient(host = BackendConfig.redisHost)

  val tokenFutures = for {
    redisAccess <- rc.smembers[AccessToken]("twitter:access_tokens")
    redisConsumer <- rc.get[ConsumerToken]("twitter:consumer_token")
    twitterBearer <- redisConsumer match { case Some(consumer) => obtainBearerToken(consumer) }
  } yield TokenBook(redisAccess, redisConsumer.getOrElse(throw new Exception("Consumer not found")), twitterBearer)

  tokenFutures onSuccess {
    case t@TokenBook(access: Seq[AccessToken], consumer: ConsumerToken, bearer: BearerToken) =>
      tokens = t
  }

  val rateLimitUri = s"$twitterScheme://$twitterHost/$twitterVersion/application/rate_limit_status.json"

  def rateLimitRequest(bearer: BearerToken) = {
    val request = Http(rateLimitUri).header("Authorization", s"Bearer ${bearer.token}").asString
    parseRateLimitResponse(request)
  }

  def rateLimitRequest(token: AccessToken) = {
    val consumer_token = Token(tokens.consumer.key, tokens.consumer.secret)
    val access_token = Token(token.key, token.secret)
    val request = Http(rateLimitUri).oauth(consumer_token, access_token).asString
    parseRateLimitResponse(request)
  }

  def parseRateLimitResponse(r: HttpResponse[String]) = Parse.decodeValidation[RateLimitArray](r.body)

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