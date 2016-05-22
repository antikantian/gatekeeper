package co.quine.gatekeeper.actors

import akka.actor._
import akka.util.{ByteString, ByteStringBuilder}
import scala.collection.mutable

object BufferActor {
  case class Packet(bs: ByteString)

  def props(client: ActorRef) = Props(new BufferActor(client))
}

class BufferActor(client: ActorRef) extends Actor with ActorLogging {

  import BufferActor._

  val validHead = Array('?', '!', '=', '+')

  val validEnd = '\n'

  val byteBuffer = mutable.ArrayBuffer[Byte]()

  def receive = {
    case bs: ByteString =>
      log.info("Received: " + bs.utf8String)
      byteBuffer ++= bs
      readBuffer(Seq.empty[ByteString]).foreach(context.parent ! Packet(_))
  }

  @scala.annotation.tailrec
  private def readBuffer(parsed: Seq[ByteString]): Seq[ByteString] = {
    if (byteBuffer.contains('\n')) {
      val pos = byteBuffer.indexOf('\n')
      val chunk = byteBuffer.slice(0, pos)
      val bs = new ByteStringBuilder()
      bs.putBytes(chunk.toArray)
      byteBuffer.remove(0, chunk.length + 1)
      readBuffer(parsed :+ bs.result)
    } else parsed
  }
}