package co.quine.gatekeeper.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.Tcp._
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import co.quine.gatekeeper._

object ClientActor {
  case object WriteAck extends Event

  def props(gate: ActorRef, client: ActorRef): Props = Props(new ClientActor(gate, client))
}

class ClientActor(gate: ActorRef, client: ActorRef) extends Actor with ActorLogging {

  import Codec._
  import context.dispatcher

  implicit val timeout = Timeout(5.seconds)

  def receive = tcp

  def tcp: Receive = {
    case Received(bs) =>
      log.info("Received: " + bs.utf8String)
      onData(bs)
  }

  def onData(bs: ByteString) = bs.head match {
    case REQUEST => onRequest(bs.utf8String)
    case UPDATE => onUpdate(bs.utf8String)
  }

  def onRequest(s: String) = s.split('|') match {
    case Array(typeId, uuid, cmdArgs) =>
      val response = cmdArgs.split(':') match {
        case Array(c, a) => onCommand(c, a)
        case Array(c) => onCommand(c)
      }
      response andThen {
        case Success(x: Token) =>
          log.info("Sending: " + Response(uuid.tail, x.serialize).serialize)
          client ! Write(ByteString(Response(uuid.tail, x.serialize).serialize))
        case Success(x: Int) => client ! Write(ByteString(Response(uuid.tail, x.toString).serialize))
        case Success(x: Long) => client ! Write(ByteString(Response(uuid.tail, x.toString).serialize))
        case Failure(_) => client ! Write(ByteString(Error(uuid.tail, "UNAVAILABLE", "DOWN").serialize))
      }
  }

  def onUpdate(s: String) = s.split('|') match {
    case Array(typeId, cmd, payload) => cmd match {
      case "RATELIMIT" =>
        payload.split(':') match { case Array(resource, tokenKey, remaining, reset) =>
          gate ! RateLimit(resource, tokenKey, remaining.toInt, reset.toLong)
        }
    }
  }

  def onCommand(cmd: String) = cmd match {
    case "CONSUMER" => gate ? Consumer
    case "NEWBEARER" => gate ? NewBearer
  }

  def onCommand(cmd: String, args: String) = cmd match {
    case "GRANT" => gate ? Grant(args)
    case "REM" => gate ? Remaining(args)
    case "TTL" => gate ? TTL(args)
  }
}