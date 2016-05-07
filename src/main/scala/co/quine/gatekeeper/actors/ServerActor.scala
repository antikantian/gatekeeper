package co.quine.gatekeeper.actors

import akka.actor._
import akka.io.{IO, Tcp}
import akka.util.Helpers
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable

import co.quine.gatekeeper.config.Config

object ServerActor {
  case class ClientDisconnected()

  val tempNumber = new AtomicLong

  def tempName = Helpers.base64(tempNumber.getAndIncrement)

  def props(gate: ActorRef): Props = Props(new ServerActor(gate))
}

class ServerActor(gate: ActorRef) extends Actor with ActorLogging {

  val host = Config.host
  val port = Config.port

  val connectedClients = mutable.Set[ActorRef]()

  IO(Tcp)(context.system) ! Tcp.Bind(self, new InetSocketAddress(host, port))

  override def preStart() = log.info(s"${self.path.name}: up")

  def receive = {
    case Tcp.Connected(remote, local) =>
      connectedClients.add(startClient(sender, remote))
    case Tcp.Bound(localAddress) =>
      log.info(s"Server bound to $localAddress")
    case Tcp.CommandFailed(cmd) =>
      log.info(s"Command failed: $cmd")
    case Terminated(actor) => connectedClients.remove(actor)
  }

  def startClient(sender: ActorRef, remoteAddress: InetSocketAddress): ActorRef = {
    val clientId = s"client-${remoteAddress.getHostString}-${ServerActor.tempName}"
    val clientActor = context.actorOf(ClientActor.props(gate, sender), clientId)
    context.watch(clientActor)
    sender ! Tcp.Register(clientActor)
    log.info(s"${remoteAddress.getHostString} connected")
    clientActor
  }

  def removeClient(client: ActorRef) = {
    connectedClients.remove(client)
    log.info(s"${client.path.name} disconnected")
  }
}


