package co.quine.gatekeeper

import akka.actor._
import co.quine.gatekeeper.Codec._

object CardCatalog {

  sealed trait IndexCard
  case class ResourceTokenCard(resource: TwitterResource, token: Token, actor: ActorRef) extends IndexCard

}
