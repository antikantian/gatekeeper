package co.quine.gatekeeper

import akka.util.ByteString
import argonaut._, Argonaut._
import redis._
import java.nio.charset.Charset

object Codec {

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

  sealed trait Sendable {
    val typeId: Char
    val uuid: String
  }

  sealed trait Request extends Sendable {
    val typeId = REQUEST
    val uuid: String
    val request: Requestable
  }

  case class TokenRequest(uuid: String, request: Resource) extends Request
  case class ConsumerRequest(uuid: String, request: ConsumerToken) extends Request
  case class NewBearerRequest(uuid: String, request: BearerToken) extends Request

  sealed trait Response extends Sendable {
    val typeId = RESPONSE
    val uuid: String
    val response: Respondable
  }

  case class TokenResponse(uuid: String, response: Token) extends Response
  case class ErrorResponse(uuid: String, response: Respondable) extends Response

  sealed trait Requestable {
    val serialized: String
  }

  case object ConsumerToken extends Requestable {
    val serialized = "CONSUMER"
  }

  case object BearerToken extends Requestable {
    val serialized = "BEARER"
  }

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

  case class AccessToken(key: String, secret: String) extends Token with Respondable {
    val typeId = ACCESSTOKEN
    val serialized = typeId + s"$key:$secret"
  }

  implicit val accessTokenByteStringFormatter = new ByteStringFormatter[AccessToken] {
    def serialize(data: AccessToken): ByteString = ByteString(s"${data.key}:${data.secret}")
    def deserialize(bs: ByteString): AccessToken = {
      bs.utf8String.split(":") match { case Array(a, b) => AccessToken(a, b) }
    }
  }

  case class ConsumerToken(key: String, secret: String) extends Token with Respondable {
    val typeId = CONSUMERTOKEN
    val serialized = typeId + s"$key:$secret"
  }

  implicit val consumerTokenByteStringFormatter = new ByteStringFormatter[ConsumerToken] {
    def serialize(data: ConsumerToken): ByteString = ByteString(s"${data.key}:${data.secret}")
    def deserialize(bs: ByteString): ConsumerToken = {
      bs.utf8String.split(":") match { case Array(a, b) => ConsumerToken(a, b) }
    }
  }

  case class BearerToken(token: String) extends Token with Respondable {
    val typeId = BEARERTOKEN
    val serialized = typeId + token
  }

  implicit def BearerTokenDecodeJson: DecodeJson[BearerToken] = {
    DecodeJson(c => for {
      bearer <- (c --\ "access_token").as[String]
    } yield BearerToken(bearer))
  }

  case class Unavailable(resource: TwitterResource, ttl: Long) extends Token with Respondable {
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

}