package co.quine.gatekeeper.actors

import akka.actor._
import com.github.nscala_time.time.Imports.DateTime

import scala.concurrent.duration._
import co.quine.twitterclient.{Bearer, Consumer, Token, TwitterApi}
import co.quine.gatekeeper.resources.TwitterResources._
import co.quine.gatekeeper.tokens.ResourceToken

object ResourceTokenActor {

  def props(token: ResourceToken)(implicit system: ActorSystem): Props = Props(new ResourceTokenActor(token))

}

class ResourceTokenActor(token: ResourceToken)(implicit system: ActorSystem) extends Actor with ActorLogging {

  require(token.tokenType == "access" || token.tokenType == "bearer")

  import system.dispatcher

  val api = token.credential match {
    case AccessToken(a, b) => TwitterApi(a.split(":")(0), a.split(":")(1), b.split(":")(0), b.split(":")(1))
    case BearerToken(a, b) => TwitterApi(b)
  }

  def updateToken(remaining: Int, reset: Long): Unit = {
    token.update(remaining, reset)
    log.info(s"${token.name} (Calls: ${token.calls}, TTL: ${token.ttl.toString}")
  }

  def runUpdate(): Unit = {
    val rls = api.rateLimitStatus()
    rls onSuccess {
      case x =>
        token.getResource match {
          case UsersLookup => updateToken(x.resources.users.lookup.remaining, x.resources.users.lookup.reset)
          case UsersShow => updateToken(x.resources.users.show.remaining, x.resources.users.show.reset)
          case StatusesLookup => updateToken(x.resources.statuses.lookup.remaining, x.resources.statuses.lookup.reset)
          case StatusesShow => updateToken(x.resources.statuses.show.remaining, x.resources.statuses.show.reset)
          case StatusesUserTimeline => updateToken(x.resources.statuses.user_timeline.remaining, x.resources.statuses.user_timeline.reset)
          case FriendsIds => updateToken(x.resources.friends.ids.remaining, x.resources.friends.ids.reset)
          case FriendsList => updateToken(x.resources.friends.list.remaining, x.resources.friends.list.reset)
          case FollowersIds => updateToken(x.resources.followers.ids.remaining, x.resources.followers.ids.reset)
          case FollowersList => updateToken(x.resources.followers.list.remaining, x.resources.followers.list.reset)
        }
    }
  }

  override def preStart() = system.scheduler.schedule(30.seconds, 5.minutes, self, "update")

  def receive = {
    case "update" => runUpdate()
  }
}