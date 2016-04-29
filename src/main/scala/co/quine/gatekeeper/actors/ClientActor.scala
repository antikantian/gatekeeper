package co.quine.gatekeeper.actors

import akka.actor._
import akka.io.Tcp
import akka.util.ByteString

import scala.util.{Failure, Success, Try}

class ClientActor extends Actor with ActorLogging {

  val buffer = new StringBuilder()

  val end = "\r\n"

  def parseRawCommand(): Option[Seq[String]] = {

    var pos = 0

    def next(length: Int = 0): String = {
      val to = if (length <= 0) buffer.indexOf(end, pos) else pos + length
      val part = buffer.slice(pos, to)

      if (part.size != to - pos) throw new Exception()
      pos = to + end.length
      part.stripLineEnd
    }

    def parts: Seq[String] = {
      val part = next()
      part.head match {
        case '-'|'+'|':' => Seq(part.tail)
        case '$'         => Seq(next(part.tail.toInt))
        case '*'         => (1 to part.tail.toInt).map(_ => parts.head)
        case _           => part.split(' ')
      }
    }

    Try(parts) match {
      case Success(output) => buffer.delete(0, pos); Some(output)
      case Failure(_)      => None
    }
  }

  override def preStart() = log.info(s"${self.path.name}: up")

  override def postStop() = log.info(s"${self.path.name}: down")

  def receive = {
    case Tcp.Received(data) => println(data.utf8String); Tcp.Write(ByteString(s"+OK$end"))
  }
}
