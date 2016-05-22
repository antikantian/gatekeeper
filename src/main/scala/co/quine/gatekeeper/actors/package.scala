package co.quine.gatekeeper

import akka.actor.ActorRef

package object actors {
  import Codec._

  sealed trait ActorArtifacts
  case class ConnectedClients(clients: Seq[ActorRef]) extends ActorArtifacts {
    def serialize = clients.map(actor => actor.path.name).mkString(",")
  }
  case class TokenBook(access: Seq[AccessToken], consumer: ConsumerToken, bearer: BearerToken) extends ActorArtifacts {
    def all: Seq[Token] = access ++ Seq[Token](bearer)

    def findByKey(key: String) = {

      (Seq[Token](consumer) ++ access) find {
        case x: AccessToken => x.key == key
        case x: ConsumerToken => x.key == key
      }
    }
  }

  case class EndpointCard(resource: TwitterResource, address: ActorRef) extends ActorArtifacts
  case class NewBearerToken(b: BearerToken) extends ActorArtifacts

  sealed trait ActorMessages
  case object ListConnectedClients extends ActorMessages
  case object NeedToken extends ActorMessages
}