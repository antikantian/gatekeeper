package co.quine.gatekeeper

import akka.actor._

import java.net.URI

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import co.quine.gatekeeper.actors.ServerActor
import co.quine.gatekeeper.config.Config

object Main extends App {

  implicit val system = ActorSystem("gatekeeper")

  val gate = new Gatekeeper()

  system.actorOf(ServerActor.props(new URI(Config.listenAddress), gate), name = "gatekeeper-server")

  Await.result(system.whenTerminated, Duration.Inf)

}