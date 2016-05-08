package co.quine.gatekeeper.connectors

import akka.actor.ActorSystem
import akka.util.Timeout
import redis.RedisClient

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import co.quine.gatekeeper.config.Config.BackendConfig

trait RedisConnector {

  implicit val system: ActorSystem
  implicit val ec: ExecutionContext

  implicit private val timeout = Timeout(60.seconds)

  val rc = new RedisClient(host = BackendConfig.redisHost)

  val pong = rc.ping

  pong.map(p => println(s"Redis: $p"))

  Await.result(pong, 20.seconds)

}