package co.quine.gatekeeper.actors

import akka.actor._
import akka.io.{IO, Udp}
import akka.util.ByteString
import java.net.InetSocketAddress

import co.quine.gatekeeper._
import co.quine.gatekeeper.config._

object UpdateActor {
  def props(gate: ActorRef) = Props(new UpdateActor(gate))
}

class UpdateActor(gate: ActorRef) extends Actor with ActorLogging {

  import Codec._
  import context.system

  val host = Config.host
  val port = Config.port

  IO(Udp) ! Udp.Bind(self, new InetSocketAddress(host, port))

  def receive = {
    case Udp.Bound(local) =>
      log.info(s"${self.path.name}: Ready")
      context.become(ready(sender()))
  }

  def ready(socket: ActorRef): Receive = {
    case Udp.Received(data, remote) =>
      onData(data)
    case Udp.Unbind => socket ! Udp.Unbind
    case Udp.Unbound => context.stop(self)
  }

  def onData(bs: ByteString) = bs.head match {
    case UPDATE => onUpdate(bs.utf8String)
  }

  def onUpdate(u: String) = u.split('|') match {
    case Array(typeId, cmd, payload) => cmd match {
      case "RATELIMIT" => gate ! rateLimitUpdate(payload)
    }
  }

  def rateLimitUpdate(payload: String): RateLimit = payload.split(':') match {
    case Array(key, resource, remaining, reset) =>
      log.info(s"$key|$remaining remaining, reset at: $reset")
      RateLimit(resource, key, remaining.toInt, reset.toLong)
  }
}