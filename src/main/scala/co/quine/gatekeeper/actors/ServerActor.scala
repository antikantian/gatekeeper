package co.quine.gatekeeper.actors

import akka.actor._
import akka.io.Tcp.Event
import akka.io.{IO, Tcp}
import akka.util.{ByteString, Helpers}
import java.net.{InetSocketAddress, URI}
import java.util.concurrent.atomic.AtomicLong

import co.quine.gatekeeper._
import co.quine.gatekeeper.resources.TwitterResources._

object ServerActor {
  val tempNumber = new AtomicLong

  def tempName = Helpers.base64(tempNumber.getAndIncrement)

  def props(listen: URI, gate: Gatekeeper): Props = Props(new ServerActor(listen, gate))
}

class ServerActor(listen: URI, gate: Gatekeeper) extends Actor with ActorLogging {

  IO(Tcp)(context.system) ! Tcp.Bind(self, new InetSocketAddress(listen.getHost, listen.getPort))

  override def preStart() = log.info("Tcp server: up")

  def receive = {
    case Tcp.Connected(remote, local) =>
      val clientActor = context.actorOf(ClientActor.props(self, sender, gate), s"gatekeeper-client-${ServerActor.tempName}")
      sender ! Tcp.Register(clientActor)
      log.info(s"${remote.getHostString} connected")
    case Tcp.Bound(localAddress) =>
      log.info(s"Server bound to $localAddress")
    case Tcp.CommandFailed(cmd) =>
      log.info(s"Command failed: $cmd")
  }
}

object ClientActor {
  case object WriteAck extends Event

  def props(server: ActorRef, client: ActorRef, gate: Gatekeeper): Props = Props(new ClientActor(server, client, gate))
}

class ClientActor(server: ActorRef, client: ActorRef, gate: Gatekeeper) extends Actor with ActorLogging {

  import ClientActor._

  var readyToWrite: Boolean = false

  override def postStop(): Unit = {
    log.info("Client disconnected")
  }

  def receive = {
    case Tcp.Received(bs) => onDataReceived(bs)
  }

  def onDataReceived(bs: ByteString) = isRequest(bs) match {
    case true => onRequestReceived(bs)
    case false =>
  }

  def onRequestReceived(bs: ByteString) = writeCredential(extractUUID(bs), extractRequest(bs))

  private def isRequest(bs: ByteString) = bs.indexOf('#') match {
    case 36 => true
    case _ => false
  }

  private def extractRequest(bs: ByteString) = bs.slice(37, bs.indexOf('\n') - 1).utf8String match {
    case "USHOW" => gate.usersShow.get
    case "ULOOKUP" => gate.usersLookup.get
    case "SLOOKUP" => gate.statusesLookup.get
    case "SSHOW" => gate.statusesShow.get
    case "SUSERTIMELINE" => gate.statusesUserTimeline.get
    case "FRIDS" => gate.friendsIds.get
    case "FRLIST" => gate.friendsList.get
    case "FOIDS" => gate.followersIds.get
    case "FOLIST" => gate.followersList.get
    case "CONSUMER" => gate.consumerToken
  }

  private def extractUUID(bs: ByteString) = bs.slice(0, bs.indexOf('#')).utf8String

  private def prependId(id: String, c: Credential) = ByteString(c.typeId + s"$id#" + c.serialized)

  private def writeCredential(id: String, c: Credential) = {
    log.info("Sending: " + prependId(id, c).utf8String)
    client ! Tcp.Write(prependId(id, c))
  }
}
