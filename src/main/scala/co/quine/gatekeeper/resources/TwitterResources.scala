package co.quine.gatekeeper.resources

import scala.collection.mutable.{Set => mSet}

object TwitterResources {

  case class TwitterTokens(consumer: ConsumerToken, bearer: BearerToken, tokens: mSet[AccessToken])

  sealed trait Context
  case object AppContext extends Context
  case object UserContext extends Context

  sealed trait Credential
  final case class AccessToken(key: String, secret: String) extends Credential
  final case class ConsumerToken(key: String, secret: String) extends Credential
  final case class BearerToken(consumer: ConsumerToken, token: String) extends Credential
  final case class NoneAvailable(resource: TwitterResource, ttl: Long) extends Credential

  sealed abstract class TwitterResource {
    val appLimit: Int
    val userLimit: Int
    val domain: String
    val title: String
    val uri: String

    def iterAll: Seq[TwitterResource] = Seq(
      UsersLookup,
      UsersShow,
      StatusesLookup,
      StatusesShow,
      StatusesUserTimeline,
      FriendsIds,
      FriendsList,
      FollowersIds,
      FollowersList
    )

    def bestContext = {
      if (appLimit > userLimit) AppContext
      else if (userLimit > appLimit) UserContext
      else UserContext
    }

    def alternateContext = bestContext match {
        case AppContext => UserContext
        case UserContext => AppContext
    }


    def lookup(uri: String): TwitterResource = {
      val pattern = "(\\/(?:users|statuses|friends|followers)\\/[a-zA-z]+.json)".r
      pattern.findFirstIn(uri) match {
        case Some("/users/show.json") => UsersShow
        case Some("/statuses/show.json") => StatusesShow
        case Some("/statuses/lookup.json") => StatusesLookup
        case Some("/users/lookup.json") => UsersLookup
        case Some("/statuses/user_timeline.json") => StatusesUserTimeline
        case Some("/friends/list.json") => FriendsList
        case Some("/followers/list.json") => FollowersList
        case Some("/friends/ids.json") => FriendsIds
        case Some("/followers/ids.json") => FollowersList
        case _ => InvalidTwitterResource
      }
    }
  }

  case object TwitterResourceStub extends TwitterResource {
    val appLimit = 0
    val userLimit = 0
    val domain = "NA"
    val title = "NA"
    val uri = "NA"
  }

  case object InvalidTwitterResource extends TwitterResource {
    val appLimit = 0
    val userLimit = 0
    val domain = "NA"
    val title = "NA"
    val uri = "NA"
  }

  case object UsersLookup extends TwitterResource {
    val appLimit = 60
    val userLimit = 180
    val domain = "users"
    val title = "/users/lookup"
    val uri = "/users/lookup.json"
  }

  case object UsersShow extends TwitterResource {
    val appLimit = 180
    val userLimit = 180
    val domain = "users"
    val title = "/users/show/:id"
    val uri = "/users/show.json"
  }

  case object StatusesLookup extends TwitterResource {
    val appLimit = 60
    val userLimit = 180
    val domain = "statuses"
    val title = "/statuses/lookup"
    val uri = "/statuses/lookup.json"
  }

  case object StatusesShow extends TwitterResource {
    val appLimit = 180
    val userLimit = 180
    val domain = "statuses"
    val title = "/statuses/show/:id"
    val uri = "/statuses/show.json"
  }

  case object StatusesUserTimeline extends TwitterResource {
    val appLimit = 300
    val userLimit = 180
    val domain = "statuses"
    val title = "/statuses/user_timeline"
    val uri = "/statuses/user_timeline.json"
  }

  case object FriendsIds extends TwitterResource {
    val appLimit = 15
    val userLimit = 15
    val domain = "friends"
    val title = "/friends/ids"
    val uri = "/friends/ids.json"
  }

  case object FriendsList extends TwitterResource {
    val appLimit = 30
    val userLimit = 15
    val domain = "friends"
    val title = "/friends/list"
    val uri = "/friends/list.json"
  }

  case object FollowersIds extends TwitterResource {
    val appLimit = 15
    val userLimit = 15
    val domain = "followers"
    val title = "/followers/ids"
    val uri = "/followers/ids.json"
  }

  case object FollowersList extends TwitterResource {
    val appLimit = 30
    val userLimit = 15
    val domain = "followers"
    val title = "/followers/list"
    val uri = "/followers/list.json"
  }
}