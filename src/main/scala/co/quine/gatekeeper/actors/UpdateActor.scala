package co.quine.gatekeeper.actors

import akka.actor._
import akka.io.{IO, Udp}
import java.net.InetSocketAddress

import co.quine.gatekeeper.config._

class UpdateActor(gate: ActorRef) extends Actor with ActorLogging {

  import context.system

  IO(Udp) ! Udp.Bind(self, new InetSocketAddress(Config.host, 0))

  def receive = {
    case Udp.Bound(local) => context.become(ready(sender()))
  }

  def ready(socket: ActorRef): Receive = {
    case Udp.Received(data, remote) =>
    case Udp.Unbind => socket ! Udp.Unbind
    case Udp.Unbound => context.stop(self)
  }
}