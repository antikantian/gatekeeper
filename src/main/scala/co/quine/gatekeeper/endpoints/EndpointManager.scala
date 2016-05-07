package co.quine.gatekeeper.endpoints

import akka.actor._
import akka.util.Timeout
import argonaut.Parse

import scalaj.http._
import scala.collection.mutable.{Set => mSet}
import scala.concurrent.Await
import scala.concurrent.duration._

import co.quine.gatekeeper._
import co.quine.gatekeeper.actors.RateLimitActor
import co.quine.gatekeeper.config.Config._
import co.quine.gatekeeper.connectors.RedisConnector
import co.quine.gatekeeper.tokens._

trait EndpointManager extends RedisConnector {
  self: Gatekeeper =>

  import Codec._

  implicit val system: ActorSystem
  implicit val gatekeeper: Gatekeeper

  implicit val timeout = Timeout(60.seconds)

  implicit val consumerToken: ConsumerToken = ConsumerToken(TwitterConfig.consumer_key, TwitterConfig.consumer_secret)
  implicit val bearerToken: BearerToken = parseBearerResponse(obtainBearer)
  implicit val accessTokens: mSet[AccessToken] = mSet.empty

  private val futureTokens = for {
    byteStrings <- rc.smembers("twitter:access_tokens")
  } yield byteStrings.map(b => b.utf8String.split(":"))

  futureTokens onSuccess {
    case x => x foreach { case Array(a, b) => accessTokens.add(AccessToken(a, b)) }
  }

  Await.result(futureTokens, 30.seconds)

  def obtainBearer: HttpResponse[String] = {
    Http("https://api.twitter.com/oauth2/token")
      .header("User-Agent", s"Gatekeeper v$version")
      .auth(consumerToken.key, consumerToken.secret)
      .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
      .postForm(Seq(("grant_type", "client_credentials")))
      .asString
  }

  def parseBearerResponse(r: HttpResponse[String]): BearerToken = {
    val raw = Parse.parseWith(r.body, _.field("access_token").flatMap(_.string).getOrElse("Error"), msg => msg)
    BearerToken(raw)
  }

  def invalidateBearer: HttpResponse[String] = {
    Http("https://api.twitter.com/oauth2/invalidate_token")
      .auth(consumerToken.key, consumerToken.secret)
      .header("User-Agent", s"Gatekeeper v$version")
      .header("Content-Type", "application/x-www-form-urlencoded")
      .postForm(Seq(("access_token", bearerToken.token)))
      .asString
  }

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