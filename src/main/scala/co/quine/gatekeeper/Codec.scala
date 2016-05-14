package co.quine.gatekeeper

import akka.util.ByteString
import argonaut._, Argonaut._
import redis._

object Codec {

  /**
    * Format: TYPE|UUID|PAYLOAD
    * Request: ?|#UUID|CMD:ARGS
    * Response: !|#UUID|[&,@,%]KEY:SECRET
    * Update: +|CMD|PAYLOAD
    * Error: -|#UUID|REASON|MESSAGE
    */

  val REQUEST = '?'
  val RESPONSE = '!'
  val REPORT = '='
  val UPDATE = '+'
  val UUID = '#'
  val ERROR = '-'
  val CONSUMERTOKEN = '&'
  val ACCESSTOKEN = '@'
  val BEARERTOKEN = '%'
  val UNAVAILABLE = '*'

  case class Request(uuid: String, cmd: String, args: String = "None") {
    val typeId = REQUEST

    def serialize = s"$typeId|$UUID$uuid|$cmd:$args"
  }

  case class Response(uuid: String, payload: String) {
    val typeId = RESPONSE

    def serialize = s"$typeId|$UUID$uuid|$payload"
  }

  case class Update(cmd: String, payload: String) {
    val typeId = UPDATE

    def serialize = s"$typeId|$cmd|$payload"
  }

  case class Report(uuid: String, payload: String) {
    val typeId = REPORT

    def serialize = s"$typeId|$UUID$uuid|$payload"
  }

  case class Error(uuid: String, reason: String, message: String) {
    val typeId = ERROR

    def serialize = s"$typeId|$reason|$message"
  }

  sealed trait Token {
    val typeId: Char

    def serialize: String
  }

  case class AccessToken(key: String, secret: String) extends Token {
    val typeId = ACCESSTOKEN

    def serialize = s"$typeId$key:$secret"
  }

  case class ConsumerToken(key: String, secret: String) extends Token {
    val typeId = CONSUMERTOKEN

    def serialize = s"$typeId$key:$secret"
  }

  case class BearerToken(key: String) extends Token {
    val typeId = BEARERTOKEN

    def serialize = s"$typeId$key"
  }

  case class UnavailableToken(ttl: Long) extends Token {
    val typeId = UNAVAILABLE

    def serialize = s"$typeId$ttl"
  }

  implicit def token2String(t: Token): String = t.serialize

  /** Redis codec for AccessToken, format = 'key:secret' */
  implicit val accessTokenByteStringFormatter = new ByteStringFormatter[AccessToken] {
    def serialize(data: AccessToken): ByteString = ByteString(s"${data.key}:${data.secret}")
    def deserialize(bs: ByteString): AccessToken = {
      bs.utf8String.split(":") match { case Array(a, b) => AccessToken(a, b) }
    }
  }

  /** Redis codec for ConsumerToken, format = 'key:secret' */
  implicit val consumerTokenByteStringFormatter = new ByteStringFormatter[ConsumerToken] {
    def serialize(data: ConsumerToken): ByteString = ByteString(s"${data.key}:${data.secret}")
    def deserialize(bs: ByteString): ConsumerToken = {
      bs.utf8String.split(":") match { case Array(a, b) => ConsumerToken(a, b) }
    }
  }

  implicit def BearerTokenDecodeJson: DecodeJson[BearerToken] = {
    DecodeJson(c => for {
      bearer <- (c --\ "access_token").as[String]
    } yield BearerToken(bearer))
  }

  sealed trait Command
  case object Consumer extends Command
  case object NewBearer extends Command
  case class Grant(resource: Resource) extends Command
  case class Remaining(resource: Resource) extends Command
  case class TTL(resource: Resource) extends Command
  case class RateLimit(resource: Resource, token: String, remaining: Int, reset: Long) extends Command

  sealed trait Context
  case object AppContext extends Context
  case object UserContext extends Context

  sealed trait Resource
  sealed trait TwitterResource extends Resource {
    val appLimit: Int
    val userLimit: Int
    val domain: String
    val title: String
    val uri: String
    val serialized: String

    def bestContext = {
      if (appLimit > userLimit) AppContext
      else if (userLimit > appLimit) UserContext
      else UserContext
    }

    def alternateContext = bestContext match {
      case AppContext => UserContext
      case UserContext => AppContext
    }
  }

  case object UsersLookup extends TwitterResource {
    val appLimit = 60
    val userLimit = 180
    val domain = "users"
    val title = "/users/lookup"
    val uri = "/users/lookup.json"
    val serialized = "ULOOKUP"
  }

  case object UsersShow extends TwitterResource {
    val appLimit = 180
    val userLimit = 180
    val domain = "users"
    val title = "/users/show/:id"
    val uri = "/users/show.json"
    val serialized = "USHOW"
  }

  case object StatusesLookup extends TwitterResource {
    val appLimit = 60
    val userLimit = 180
    val domain = "statuses"
    val title = "/statuses/lookup"
    val uri = "/statuses/lookup.json"
    val serialized = "SLOOKUP"
  }

  case object StatusesShow extends TwitterResource {
    val appLimit = 180
    val userLimit = 180
    val domain = "statuses"
    val title = "/statuses/show/:id"
    val uri = "/statuses/show.json"
    val serialized = "SSHOW"
  }

  case object StatusesUserTimeline extends TwitterResource {
    val appLimit = 300
    val userLimit = 180
    val domain = "statuses"
    val title = "/statuses/user_timeline"
    val uri = "/statuses/user_timeline.json"
    val serialized = "SUSERTIMELINE"
  }

  case object FriendsIds extends TwitterResource {
    val appLimit = 15
    val userLimit = 15
    val domain = "friends"
    val title = "/friends/ids"
    val uri = "/friends/ids.json"
    val serialized = "FRIDS"
  }

  case object FriendsList extends TwitterResource {
    val appLimit = 30
    val userLimit = 15
    val domain = "friends"
    val title = "/friends/list"
    val uri = "/friends/list.json"
    val serialized = "FRLIST"
  }

  case object FollowersIds extends TwitterResource {
    val appLimit = 15
    val userLimit = 15
    val domain = "followers"
    val title = "/followers/ids"
    val uri = "/followers/ids.json"
    val serialized = "FOIDS"
  }

  case object FollowersList extends TwitterResource {
    val appLimit = 30
    val userLimit = 15
    val domain = "followers"
    val title = "/followers/list"
    val uri = "/followers/list.json"
    val serialized = "FOLIST"
  }

  implicit def string2resource(s: String): Resource = s match {
    case "ULOOKUP" => UsersLookup
    case "USHOW" => UsersShow
    case "SLOOKUP" => StatusesLookup
    case "SSHOW" => StatusesShow
    case "SUSERTIMELINE" => StatusesUserTimeline
    case "FRIDS" => FriendsIds
    case "FRLIST" => FriendsList
    case "FOIDS" => FollowersIds
    case "FOLIST" => FollowersList
  }

}