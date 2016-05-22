package co.quine.gatekeeper
package actors

import akka.actor._
import akka.io.Tcp._
import akka.util.ByteString
import scala.concurrent.duration._

object HeartbeatActor {
  def props(client: ActorRef) = Props(new HeartbeatActor(client))
}

class HeartbeatActor(client: ActorRef) extends Actor with ActorLogging {

  import Codec._
  import context.dispatcher

  val heartbeat = context.system.scheduler.schedule(10.seconds, 10.seconds, self, "tick")

  def receive = {
    case "tick" =>
      val heartbeat: String = TICK + "\n"
      client ! Write(ByteString(heartbeat))
  }
}