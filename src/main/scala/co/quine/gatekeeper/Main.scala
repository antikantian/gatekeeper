package co.quine.gatekeeper

import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import java.net.URI

import akka.stream.scaladsl.Tcp._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

import co.quine.gatekeeper.actors.ServerActor
import co.quine.gatekeeper.config.Config
import co.quine.gatekeeper.actors._

object Main extends App {

  implicit val system = ActorSystem("gatekeeper")

  val gatekeeper = system.actorOf(GateActor.props(), name = "gatekeeper")

  val server = system.actorOf(Server)

}