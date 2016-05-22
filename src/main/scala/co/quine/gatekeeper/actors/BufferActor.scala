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
      readBuffer(List.empty[ByteString]).foreach(context.parent ! Packet(_))
  }

  @scala.annotation.tailrec
  private def readBuffer(parsed: List[ByteString]): List[ByteString] = {
    if (byteBuffer.contains('\n') && validHead.exists(byteBuffer.contains)) {
      val start = byteBuffer.indexWhere(validHead.contains)
      val end = byteBuffer.indexOf('\n', start)
      val chunk = byteBuffer.slice(start, end).foldLeft(new ByteStringBuilder())((bs, byte) => bs += byte)
      byteBuffer.trimStart(chunk.length + 1)
      readBuffer(parsed :+ chunk.result)
    } else parsed
  }
}