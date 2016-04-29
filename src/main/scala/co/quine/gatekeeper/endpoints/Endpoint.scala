package co.quine.gatekeeper.endpoints

import akka.actor._

import scala.collection.mutable.{Set => mSet}

import co.quine.gatekeeper.Gatekeeper
import co.quine.gatekeeper.resources.TwitterResources._
import co.quine.gatekeeper.tokens.{ResourceToken, TokenArray}

class Endpoint(uri: TwitterResource)(
  implicit val system: ActorSystem,
  implicit val gatekeeper: Gatekeeper,
  implicit val consumerToken: ConsumerToken,
  implicit val bearerToken: BearerToken,
  implicit val accessTokens: mSet[AccessToken]) {

  implicit val ec = system.dispatcher

  private val tokens = {
    val tArray = new TokenArray(uri)
    tArray.add(bearerToken)
    accessTokens.foreach(a => tArray.add(a))
    tArray
  }

  val name = uri.title
  val resourceType = uri
  val resourceUri = uri.uri

  def exchangeBearer(invalid: BearerToken, valid: BearerToken) = {
    require(invalid.token != valid.token)

    tokens.bearerTokens.filter(r => r.key == invalid.token) map { x =>
      tokens.bearerTokens.remove(x)
    }

    tokens.add(valid)
  }

  def getResourceTokens: mSet[ResourceToken] = tokens.tokens.union(tokens.bearerTokens)

  def hasCalls = tokens.hasCalls

  def remaining = tokens.remaining

  def ttl: Long = tokens.ttl

  def get: Credential = tokens.grant

}