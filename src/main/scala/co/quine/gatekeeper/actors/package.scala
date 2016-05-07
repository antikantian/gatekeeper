package co.quine.gatekeeper

import akka.actor.ActorRef

package object actors {
  import Codec._

  sealed trait ActorArtifacts
  case class TokenBook(access: Seq[AccessToken], consumer: ConsumerToken, bearer: BearerToken) extends ActorArtifacts
  case class EndpointCard(resource: TwitterResource, address: ActorRef) extends ActorArtifacts

  sealed trait ActorMessages
  case object NeedToken extends ActorMessages
}