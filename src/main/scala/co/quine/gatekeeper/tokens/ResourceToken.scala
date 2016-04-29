package co.quine.gatekeeper.tokens

import co.quine.gatekeeper.actors.RateLimitActor.RateLimitStatus
import co.quine.gatekeeper.resources.TwitterResources._

object ResourceToken {

  def apply(token: Credential)(implicit resource: TwitterResource) = new ResourceToken(resource, token)

}

class ResourceToken(resource: TwitterResource, token: Credential) {

  private var remaining: Int = 0

  private var resetTime: Long = -1

  val name = resource.uri

  val key = token match {
    case AccessToken(a, b) => a
    case BearerToken(a, b) => a.key
  }

  val tokenType = token match {
    case AccessToken(_, _) => "access"
    case BearerToken(_, _) => "bearer"
    case _ => "NA"
  }

  def getResource: TwitterResource = resource

  def update(status: RateLimitStatus): Unit = resource match {
      case UsersLookup => update(status.resources.users.lookup.remaining, status.resources.users.lookup.reset)
      case UsersShow => update(status.resources.users.show.remaining, status.resources.users.show.reset)
      case StatusesLookup => update(status.resources.statuses.lookup.remaining, status.resources.statuses.lookup.reset)
      case StatusesShow => update(status.resources.statuses.show.remaining, status.resources.statuses.show.reset)
      case StatusesUserTimeline => update(status.resources.statuses.user_timeline.remaining, status.resources.statuses.user_timeline.reset)
      case FriendsIds => update(status.resources.friends.ids.remaining, status.resources.friends.ids.reset)
      case FriendsList => update(status.resources.friends.list.remaining, status.resources.friends.list.reset)
      case FollowersIds => update(status.resources.followers.ids.remaining, status.resources.followers.ids.reset)
      case FollowersList => update(status.resources.followers.list.remaining, status.resources.followers.list.reset)
  }

  def update(rem: Int, res: Long) = {
    remaining = rem
    resetTime = res
  }

  def hasCalls: Boolean = {
    if (remaining <= 0) false
    else true
  }

  def credential = token

  def take = if (remaining - 1 >= 0) {
    remaining -= 1
    token
  } else NoneAvailable(resource, ttl)

  def ttl: Long = resetTime

  def calls: Int = remaining

}