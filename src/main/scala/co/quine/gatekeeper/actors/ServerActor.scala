package co.quine.gatekeeper.actors

import akka.actor._
import akka.io.{IO, Tcp}

import java.net.{InetSocketAddress, URI}

import co.quine.gatekeeper.protocol._

object ServerActor {
  def props(listen: URI): Props = Props(new ServerActor(listen))
}

class ServerActor(listen: URI) extends Actor with ActorLogging {

  IO(Tcp)(context.system) ! Tcp.Bind(self, new InetSocketAddress(listen.getHost, listen.getPort))

  override def preStart() = log.info("Tcp server: up")

  def receive = {
    case Tcp.Connected(remote, local) =>
      val clientActor = context.actorOf(ConnectionActor.props(self))
      sender ! Tcp.Register(clientActor)
      log.info(s"${remote.getHostString} connected")
    case Tcp.Bound(localAddress) =>
      log.info(s"Server bound to $localAddress")
    case Tcp.CommandFailed(cmd) =>
      log.info(s"Command failed: $cmd")
  }
}

object ConnectionActor {
  def props(server: ActorRef): Props = Props(new ConnectionActor(server))
}

class ConnectionActor(server: ActorRef) extends Actor with ActorLogging {

  def receive = {
    case Tcp.Received(data) => println(data.utf8String)
    case Command.Disconnect =>
      log.info("Client disconnected")
      context.stop(self)
    case Tcp.PeerClosed =>
      log.info("Client disconnected")
      context.stop(self)
    case msg => log.info(s"Received unknown: $msg")
  }

}

