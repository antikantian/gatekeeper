package co.quine.gatekeeper

import akka.actor._

import scala.collection.mutable.{Set => mSet}

import co.quine.gatekeeper.endpoints.{EndpointManager, Endpoint}

class Gatekeeper()(implicit val system: ActorSystem) extends EndpointManager {

  implicit val gatekeeper = this

}