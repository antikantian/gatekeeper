package co.quine.gatekeeper

import akka.actor._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import co.quine.gatekeeper.actors._

object Gatekeeper extends App {

  implicit val system = ActorSystem("gatekeeper-server")

  val gatekeeper = system.actorOf(GatekeeperActor.props, "gatekeeper-main")
  val server = system.actorOf(ServerActor.props(gatekeeper), "gatekeeper-server")

  Await.result(system.whenTerminated, Duration.Inf)

}