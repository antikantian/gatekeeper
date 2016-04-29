package co.quine.gatekeeper.server

import akka.actor._

object Server extends App {

  implicit val system = ActorSystem("gatekeeper")

}