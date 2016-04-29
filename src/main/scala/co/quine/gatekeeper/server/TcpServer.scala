package co.quine.gatekeeper.server

import akka.actor._
import akka.event.LoggingReceive
import akka.io.{IO, Tcp}
import java.net.{InetSocketAddress, URI}

class TcpServer(listen: URI) extends Actor with ActorLogging {

  IO(Tcp)(context.system) ! Tcp.Bind(self, new InetSocketAddress(listen.getHost, listen.getPort))

  def receive: Receive = LoggingReceive {
    case _: Tcp.Connected => sender() ! Tcp.Register(context.actorOf(Props[TcpClient]))
  }
}