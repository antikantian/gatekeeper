package co.quine.gatekeeper.tokens

import akka.actor.ActorSystem

import scala.collection.mutable.{Set => mSet}
import co.quine.gatekeeper.Codec._

class TokenArray(uri: TwitterResource)(implicit val system: ActorSystem) {

  implicit val resource = uri

  val tokens: mSet[ResourceToken] = mSet.empty
  val bearerTokens: mSet[ResourceToken] = mSet.empty

  def add(token: Token) = token match {
    case AccessToken(a, b) => tokens.add { new ResourceToken(uri, AccessToken(a, b)) }
    case BearerToken(a) => bearerTokens.add { new ResourceToken(uri, BearerToken(a)) }
  }

  private def tokensWithCalls = tokens.filter(rt => rt.hasCalls)

  private def bearerTokensWithCalls = bearerTokens.filter(rt => rt.hasCalls)

  private def minReset: ResourceToken = tokens.reduceLeft { (a, b) => if (a.ttl <= b.ttl) a else b }

  private def maxCalls: ResourceToken = tokensWithCalls.reduceLeft { (a, b) => if (a.calls >= b.calls) a else b }

  private def bearerMinReset: ResourceToken = bearerTokens.reduceLeft { (a, b) => if (a.ttl <= b.ttl) a else b }

  private def bearerMaxCalls: ResourceToken = bearerTokensWithCalls.reduceLeft { (a, b) => if (a.calls >= b.calls) a else b }

  def hasCalls = if (tokensWithCalls.nonEmpty || bearerTokensWithCalls.nonEmpty) true else false

  def remaining = tokensWithCalls.union(bearerTokensWithCalls).foldLeft(0) { (x, y) => x + y.calls }

  def ttl: Long = math.min(minReset.ttl, bearerMinReset.ttl)

  def grant: Token = {
    if (hasCalls) uri.bestContext match {
      case AppContext =>
        if (bearerTokensWithCalls.nonEmpty) bearerMaxCalls.take else maxCalls.take
      case UserContext =>
        if (tokensWithCalls.nonEmpty) maxCalls.take else bearerMaxCalls.take
    }
    else Unavailable(uri, ttl)
  }
}
