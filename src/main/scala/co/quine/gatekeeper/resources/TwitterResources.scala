package co.quine.gatekeeper.resources

import akka.util.ByteString
import argonaut._, Argonaut._

import co.quine.gatekeeper.protocol._

object TwitterResources {

  sealed trait ClientRequest {
    val uuid: String
    val request: String
  }

  case class EndpointRequest(uuid: String, request: String) extends ClientRequest
  case class ConsumerRequest(uuid: String, request: String) extends ClientRequest

  sealed trait Context
  case object AppContext extends Context
  case object UserContext extends Context

  sealed trait Credential {
    val serialized: String
    val typeId: String

    val encoded: ByteString = encode(typeId + serialized)

    def encode(credential: String) = ResponseProtocol.inline(credential)
  }

  final case class AccessToken(key: String, secret: String) extends Credential {
    val typeId = "@"
    val serialized = s"$key:$secret"
  }

  final case class ConsumerToken(key: String, secret: String) extends Credential {
    val typeId = "&"
    val serialized = s"$key:$secret"
  }

  final case class BearerToken(consumer: ConsumerToken, token: String) extends Credential {
    val typeId = "$"
    val serialized = token
  }

  final case class NoneAvailable(resource: TwitterResource, ttl: Long) extends Credential {
    val typeId = "*"
    val serialized = s"${resource.encoded}:$ttl"
  }

  implicit def AccessTokenEncodeJson: EncodeJson[AccessToken] = {
    EncodeJson((t: AccessToken) =>
      ("token_type" := t.typeId) ->: ("key" := t.key) ->: ("secret" := t.secret) ->: jEmptyObject)
  }

  implicit def ConsumerTokenEncodeJson: EncodeJson[ConsumerToken] = {
    EncodeJson((t: ConsumerToken) =>
      ("token_type" := t.typeId) ->: ("key" := t.key) ->: ("secret" := t.secret) ->: jEmptyObject)
  }

  implicit def BearerTokenEncodeJson: EncodeJson[BearerToken] = {
    EncodeJson((t: BearerToken) =>
      ("token_type" := t.typeId) ->: ("consumer" := t.consumer) ->: ("token" := t.token) ->: jEmptyObject)
  }

  implicit def NoneAvailableEncodeJson: EncodeJson[NoneAvailable] = {
    EncodeJson((t: NoneAvailable) =>
      ("token_type" := t.typeId) ->: ("resource" := t.resource.uri) ->: ("ttl" := t.ttl) ->: jEmptyObject)
  }

  sealed abstract class TwitterResource {
    val appLimit: Int
    val userLimit: Int
    val domain: String
    val title: String
    val uri: String
    val encoded: String

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
    val encoded = "ULOOKUP"
  }

  case object UsersShow extends TwitterResource {
    val appLimit = 180
    val userLimit = 180
    val domain = "users"
    val title = "/users/show/:id"
    val uri = "/users/show.json"
    val encoded = "USHOW"
  }

  case object StatusesLookup extends TwitterResource {
    val appLimit = 60
    val userLimit = 180
    val domain = "statuses"
    val title = "/statuses/lookup"
    val uri = "/statuses/lookup.json"
    val encoded = "SLOOKUP"
  }

  case object StatusesShow extends TwitterResource {
    val appLimit = 180
    val userLimit = 180
    val domain = "statuses"
    val title = "/statuses/show/:id"
    val uri = "/statuses/show.json"
    val encoded = "SSHOW"
  }

  case object StatusesUserTimeline extends TwitterResource {
    val appLimit = 300
    val userLimit = 180
    val domain = "statuses"
    val title = "/statuses/user_timeline"
    val uri = "/statuses/user_timeline.json"
    val encoded = "SUSERTIMELINE"
  }

  case object FriendsIds extends TwitterResource {
    val appLimit = 15
    val userLimit = 15
    val domain = "friends"
    val title = "/friends/ids"
    val uri = "/friends/ids.json"
    val encoded = "FRIDS"
  }

  case object FriendsList extends TwitterResource {
    val appLimit = 30
    val userLimit = 15
    val domain = "friends"
    val title = "/friends/list"
    val uri = "/friends/list.json"
    val encoded = "FRLIST"
  }

  case object FollowersIds extends TwitterResource {
    val appLimit = 15
    val userLimit = 15
    val domain = "followers"
    val title = "/followers/ids"
    val uri = "/followers/ids.json"
    val encoded = "FOIDS"
  }

  case object FollowersList extends TwitterResource {
    val appLimit = 30
    val userLimit = 15
    val domain = "followers"
    val title = "/followers/list"
    val uri = "/followers/list.json"
    val encoded = "FOLIST"
  }
}