package co.quine.gatekeeper.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.Tcp._
import akka.util.ByteString

import co.quine.gatekeeper._

object ClientActor {
  case object WriteAck extends Event

  def props(gate: ActorRef, client: ActorRef): Props = Props(new ClientActor(gate, client))
}

class ClientActor(gate: ActorRef, client: ActorRef) extends Actor with ActorLogging {

  import Codec._
  import Deserializer._
  import Serializer._

  def receive = gatekeeper orElse tcp

  def gatekeeper: Receive = {
    case r: Response => onResponse(r)
  }

  def tcp: Receive = {
    case Received(bs) => onData(bs)
  }

  def onData(bs: ByteString) = bs.deserialize match {
    case s: Request => gate ! s
    case s: Update => gate ! s
  }

  def onResponse(r: Response) = r match {
    case r @ TokenResponse(uuid, token) => client ! Write(r.encode)
  }

}