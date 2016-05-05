package co.quine.gatekeeper.protocol

import akka.util.ByteString
import java.nio.charset.Charset

object ResponseProtocol {
  val UTF8_CHARSET = Charset.forName("UTF-8")
  val LS_STRING = "\r\n"
  val LS = LS_STRING.getBytes(UTF8_CHARSET)

  def inline(credential: String): ByteString = ByteString(credential + LS_STRING)

}