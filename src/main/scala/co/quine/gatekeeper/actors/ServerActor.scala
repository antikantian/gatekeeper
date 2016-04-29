package co.quine.gatekeeper.actors

import akka.actor._
import akka.event.LoggingReceive
import akka.io.{IO, Tcp}

import java.net.{InetSocketAddress, URI}

object ServerActor {
  def props(listen: URI): Props = Props(new ServerActor(listen))
}

class ServerActor(listen: URI) extends Actor with ActorLogging {

  IO(Tcp)(context.system) ! Tcp.Bind(self, new InetSocketAddress(listen.getHost, listen.getPort))

  override def preStart() = log.info("Tcp server: up")

  def receive: Receive = LoggingReceive {
    case _: Tcp.Connected => sender() ! Tcp.Register(context.actorOf(Props[ClientActor], "gatekeeper-client"))
  }

}