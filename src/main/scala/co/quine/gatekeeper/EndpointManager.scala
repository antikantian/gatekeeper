package co.quine.gatekeeper

import akka.actor._
import akka.util.Timeout
import argonaut.Parse

import co.quine.gatekeeper.actors._
import co.quine.gatekeeper.config.Config._
import co.quine.gatekeeper.endpoints.Endpoint
import co.quine.gatekeeper.tokens._

import scala.collection.mutable.{Set => mSet}
import scala.concurrent.Await
import scala.concurrent.duration._
import scalaj.http._

trait EndpointManager {

  import Codec._

  implicit val system: ActorSystem

  implicit val timeout = Timeout(60.seconds)

  val endpointMap: Map[TwitterResource, Endpoint] = Map(
    UsersLookup -> new Endpoint(UsersLookup),
    UsersShow -> new Endpoint(UsersShow),
    StatusesLookup -> new Endpoint(StatusesLookup),
    StatusesShow -> new Endpoint(StatusesShow),
    StatusesUserTimeline -> new Endpoint(StatusesUserTimeline),
    FriendsIds -> new Endpoint(FriendsIds),
    FriendsList -> new Endpoint(FriendsList),
    FollowersIds -> new Endpoint(FollowersIds),
    FollowersList -> new Endpoint(FollowersList)
  )

  val rateLimitActor = system.actorOf(RateLimitActor.props(allResourceTokens, consumerToken), "rate-limit-updates")

  def exchangeBearer(b: BearerToken) = endpointMap.values.foreach(e => e.exchangeBearer(bearerToken, b))

  def allResourceTokens: Seq[ResourceToken] = endpointMap.values.flatMap(e => e.getResourceTokens).toSeq

  def uniqueResources: Seq[ResourceToken] = allResourceTokens.groupBy(_.key).map(_._2.head).toSeq

  def usersLookup = endpointMap(UsersLookup)

  def usersShow = endpointMap(UsersShow)

  def statusesLookup = endpointMap(StatusesLookup)

  def statusesShow = endpointMap(StatusesShow)

  def statusesUserTimeline = endpointMap(StatusesUserTimeline)

  def friendsIds = endpointMap(FriendsIds)

  def friendsList = endpointMap(FriendsList)

  def followersIds = endpointMap(FollowersIds)

  def followersList = endpointMap(FollowersList)

}