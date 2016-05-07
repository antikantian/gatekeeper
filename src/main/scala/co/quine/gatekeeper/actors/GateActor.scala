package co.quine.gatekeeper.actors

import akka.actor._
import co.quine.gatekeeper._

object GateActor {
  def props() = Props(new GateActor)
}

class GateActor extends Actor with ActorLogging {

  import Codec._

  val gate = new Gatekeeper()

  def receive = {
    case r: Request => sender ! onRequest(r)
    case r: Update =>
  }

  def onRequest(r: Request): Response = r match {
    case TokenRequest(uuid, resource) =>
      val token = getResourceToken(resource)
      TokenResponse(uuid, token)
    case ConsumerRequest(uuid, _) =>
      val token = gate.consumerToken
      TokenResponse(uuid, token)
  }

  def onUpdate(u: Update): Unit = u match {
    case RateLimit(token, resource, remaining, ttl) =>
  }

  def rateLimitUpdate(r: RateLimit): Unit = {
    val endpoint = gate.endpointMap(r.resource)

  }

  def getResourceToken(r: Resource): Token = r match {
    case UsersLookup => gate.usersLookup.get
    case UsersShow => gate.usersShow.get
    case StatusesLookup => gate.statusesLookup.get
    case StatusesShow => gate.statusesShow.get
    case StatusesUserTimeline => gate.statusesUserTimeline.get
    case FriendsIds => gate.friendsIds.get
    case FriendsList => gate.friendsList.get
    case FollowersIds => gate.followersIds.get
    case FollowersList => gate.followersList.get
  }
}