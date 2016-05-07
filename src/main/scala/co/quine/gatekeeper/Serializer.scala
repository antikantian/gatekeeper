package co.quine.gatekeeper

import akka.util.ByteString

object Serializer {

  import Codec._

  implicit class Serializer(s: Sendable) {
    def serialize = s match {
      case r: Request => Seq(s"$UUID${r.uuid}", s"${r.typeId}${r.request.serialized}").mkString("^")
      case r: Response => Seq(s"$UUID${r.uuid}", s"${r.typeId}${r.response.serialized}").mkString("^")
      case r: Update => Seq(s"$UUID${r.uuid}", s"${r.typeId}${r.updateType}${r.payload}").mkString("^")
    }

    def encode = ByteString(s.serialize + LS_STRING)
  }

}