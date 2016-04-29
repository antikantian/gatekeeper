package co.quine.gatekeeper.actors

import akka.actor._

class ListenerActor extends Actor {

  def receive = {
    case msg => println(msg)
  }

}