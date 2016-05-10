package co.quine.gatekeeper

import akka.util.ByteString
import argonaut._, Argonaut._
import redis._
import java.nio.charset.Charset

object Codec {

  /** Constants */
  val UTF8_CHARSET = Charset.forName("UTF-8")
  val LS_STRING = "\r\n"
  val LS = LS_STRING.getBytes(UTF8_CHARSET)

  val REQUEST = '?'
  val RESPONSE = '!'
  val UPDATE = '>'
  val COMMAND = '+'

  val UUID = '#'

  val ERROR = '-'

  val UNAVAILABLE = '*'
  val CONSUMERTOKEN = '&'
  val ACCESSTOKEN = '@'
  val BEARERTOKEN = '%'

  /** Sendables are objects that can be serialized and passed around between client and server */
  sealed trait Sendable {
    val typeId: Char
    val uuid: String
  }

  implicit class Serializer(s: Sendable) {
    def serialize = s match {
      case r: Request => Seq(s"$UUID${r.uuid}", s"${r.typeId}${r.request.serialized}").mkString("^")
      case r: Response => Seq(s"$UUID${r.uuid}", s"${r.typeId}${r.response.serialized}").mkString("^")
      case r: Update => Seq(s"$UUID${r.uuid}", s"${r.typeId}${r.updateType}${r.payload}").mkString("^")
    }

    def encode = ByteString(s.serialize + LS_STRING)
  }

  sealed trait Command extends Sendable {
    val typeId = COMMAND
    val uuid = java.util.UUID.randomUUID.toString
    val cmd: String
  }

  case class Remaining(resource: TwitterResource) extends Command {
    val cmd = s"REM:${resource.serialized}"
  }

  case class TTL(resource: TwitterResource) extends Command {
    val cmd = s"TTL:${resource.serialized}"
  }

  /** Updates are sendables that go from client <--> server */
  sealed trait Update extends Sendable {
    val typeId = UPDATE
    val uuid = java.util.UUID.randomUUID.toString
    val updateType: Char
    val payload: String
  }

  case class RateLimit(token: Token, resource: TwitterResource, remaining: Int, ttl: Long) extends Update {
    val updateType = 'R'
    val payload = s"${token.serialized}[]${resource.serialized}[]${remaining.toString}[]${ttl.toString}"
  }

  /** Requests are sendables that go from client --> server.  They take Requestables as a parameter */
  sealed trait Request extends Sendable {
    val typeId = REQUEST
    val uuid: String
    val request: Requestable
  }

  case class TokenRequest(uuid: String, request: Resource) extends Request
  case class ConsumerRequest(uuid: String, request: Requestable) extends Request
  case class NewBearerRequest(uuid: String, request: Requestable) extends Request

  /** Requestables are objects that denote what is being requested */
  sealed trait Requestable {
    val serialized: String
  }

  case object ConsumerToken extends Requestable {
    val serialized = "CONSUMER"
  }

  case object BearerToken extends Requestable {
    val serialized = "BEARER"
  }

  /** Responses are sendables that go from server --> client */
  sealed trait Response extends Sendable {
    val typeId = RESPONSE
    val uuid: String
    val response: Respondable
  }

  case class TokenResponse(uuid: String, response: Token) extends Response
  case class ErrorResponse(uuid: String, response: Respondable) extends Response

  /** Respondables are counterpart objects to Requestables and flow from server --> client */
  sealed trait Respondable {
    val typeId: Char
    val serialized: String
  }

  sealed trait Error extends Respondable {
    val typeId = '-'
    val serialized: String
  }

  case object ResourceDown extends Error {
    val serialized = typeId + "RESOURCEDOWN"
  }

  sealed trait Token extends Respondable {
    val typeId: Char
    val serialized: String
  }

  case class AccessToken(key: String, secret: String) extends Token {
    val typeId = ACCESSTOKEN
    val serialized = typeId + s"$key:$secret"
  }

  /** Redis codec for AccessToken, format = 'key:secret' */
  implicit val accessTokenByteStringFormatter = new ByteStringFormatter[AccessToken] {
    def serialize(data: AccessToken): ByteString = ByteString(s"${data.key}:${data.secret}")
    def deserialize(bs: ByteString): AccessToken = {
      bs.utf8String.split(":") match { case Array(a, b) => AccessToken(a, b) }
    }
  }

  case class ConsumerToken(key: String, secret: String) extends Token {
    val typeId = CONSUMERTOKEN
    val serialized = typeId + s"$key:$secret"
  }

  /** Redis codec for ConsumerToken, format = 'key:secret' */
  implicit val consumerTokenByteStringFormatter = new ByteStringFormatter[ConsumerToken] {
    def serialize(data: ConsumerToken): ByteString = ByteString(s"${data.key}:${data.secret}")
    def deserialize(bs: ByteString): ConsumerToken = {
      bs.utf8String.split(":") match { case Array(a, b) => ConsumerToken(a, b) }
    }
  }

  case class BearerToken(token: String) extends Token {
    val typeId = BEARERTOKEN
    val serialized = typeId + token
  }

  implicit def BearerTokenDecodeJson: DecodeJson[BearerToken] = {
    DecodeJson(c => for {
      bearer <- (c --\ "access_token").as[String]
    } yield BearerToken(bearer))
  }

  case class Unavailable(resource: TwitterResource, ttl: Long) extends Token {
    val typeId = UNAVAILABLE
    val serialized = typeId + s"${resource.serialized}:$ttl"
  }

  sealed trait Consumable {
    val serialized: String
  }

  sealed trait Context
  case object AppContext extends Context
  case object UserContext extends Context

  sealed trait Resource extends Consumable with Requestable
  sealed trait TwitterResource extends Resource {
    val appLimit: Int
    val userLimit: Int
    val domain: String
    val title: String
    val uri: String

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

  /** Deserializer from bytestring -> string -> object */
  implicit class Deserializer(bs: ByteString) {
    def deserialize = {

      val s = bs.utf8String.diff(LS_STRING)

      val parts = s.split('^')

      val uuid = parts.head.tail

      val msgType = parts.tail match {
        case Array(x) => x.head match {
          case REQUEST => deserializeRequest(x.tail)
          case RESPONSE => deserializeResponse(x.tail)
          case UPDATE => deserializeUpdate(x.tail)
        }
      }

      msgType match {
        case m: Requestable => m match {
          case r: TwitterResource => TokenRequest(uuid, r)
          case r: ConsumerToken => ConsumerRequest(uuid, r)
        }

        case m: Respondable => m match {
          case r: Token => TokenResponse(uuid, r)
        }

        case m: Update => m match {
          case r @ RateLimit(token, resource, remaining, ttl) => r
        }
      }
    }
  }

  private def deserializeRequest(s: String) = s match {
    case "ULOOKUP" => UsersLookup
    case "USHOW" => UsersShow
    case "SLOOKUP" => StatusesLookup
    case "SSHOW" => StatusesShow
    case "SUSERTIMELINE" => StatusesUserTimeline
    case "FRIDS" => FriendsIds
    case "FRLIST" => FriendsList
    case "FOIDS" => FollowersIds
    case "FOLIST" => FollowersList
    case "CONSUMER" => ConsumerToken
  }

  private def deserializeResponse(s: String) = s.head match {
    case ACCESSTOKEN => deserializeAccess(s.tail)
    case BEARERTOKEN => deserializeBearer(s.tail)
    case CONSUMERTOKEN => deserializeConsumer(s.tail)
    case UNAVAILABLE => deserializeUnavailable(s.tail)
  }

  private def deserializeUpdate(s: String) = s.head match {
    case 'R' => deserializeRateLimit(s.tail)
  }

  private def deserializeRateLimit(s: String) = s.split("[]") match {
    case Array(w, x, y, z) =>
      val token = deserializeResponse(w)
      val resource = deserializeResource(x)
      val remaining = y.toInt
      val ttl = z.toLong
      RateLimit(token, resource, remaining, ttl)
  }

  private def deserializeAccess(s: String) = s.split(":") match {
    case Array(x, y) => AccessToken(x, y)
  }

  private def deserializeBearer(s: String) = BearerToken(s)

  private def deserializeConsumer(s: String) = s.split(":") match {
    case Array(x, y) => ConsumerToken(x, y)
  }

  private def deserializeUnavailable(s: String) = s.split(":") match {
    case Array(x, y) =>
      val resource = deserializeResource(x)
      val ttl = y.toLong
      Unavailable(resource, ttl)
  }

  private def deserializeResource(s: String) = s match {
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