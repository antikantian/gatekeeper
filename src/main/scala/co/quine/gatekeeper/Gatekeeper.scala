package co.quine.gatekeeper

import akka.actor._

import co.quine.gatekeeper.endpoints._

class Gatekeeper()(implicit val system: ActorSystem)
  extends EndpointManager {

  implicit val gatekeeper = this

}