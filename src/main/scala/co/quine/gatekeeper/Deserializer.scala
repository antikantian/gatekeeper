package co.quine.gatekeeper

import akka.util.ByteString

object Deserializer {

  import Codec._

  implicit class Deserializer(bs: ByteString) {
    def deserialize = {

      val s = bs.utf8String.diff(LS_STRING)

      val parts = s.split("^")

      val uuid = parts.head.tail

      val msgType = parts.tail match {
        case Array(x) => x.head match {
          case REQUEST => deserializeRequest(x.tail)
          case RESPONSE => deserializeResponse(x.tail)
          case UPDATE => deserializeUpdate(x.tail)
        }
      }

      msgType match {
        case m: Requestable => m match {
          case r: TwitterResource => TokenRequest(uuid, r)
          case r: ConsumerToken => ConsumerRequest(uuid, r)
        }
        case m: Respondable => m match {
          case r: Token => TokenResponse(uuid, r)
        }
        case m: Update => m match {
          case r @ RateLimit(token, resource, remaining, ttl) => r
        }
      }
    }
  }

  private def deserializeRequest(s: String) = s match {
    case "ULOOKUP" => UsersLookup
    case "USHOW" => UsersShow
    case "SLOOKUP" => StatusesLookup
    case "SSHOW" => StatusesShow
    case "SUSERTIMELINE" => StatusesUserTimeline
    case "FRIDS" => FriendsIds
    case "FRLIST" => FriendsList
    case "FOIDS" => FollowersIds
    case "FOLIST" => FollowersList
    case "CONSUMER" => ConsumerToken
  }

  private def deserializeResponse(s: String) = s.head match {
    case ACCESSTOKEN => deserializeAccess(s.tail)
    case BEARERTOKEN => deserializeBearer(s.tail)
    case CONSUMERTOKEN => deserializeConsumer(s.tail)
    case UNAVAILABLE => deserializeUnavailable(s.tail)
  }

  private def deserializeUpdate(s: String) = s.head match {
    case 'R' => deserializeRateLimit(s.tail)
  }

  private def deserializeRateLimit(s: String) = s.split("[]") match {
    case Array(w, x, y, z) =>
      val token = deserializeResponse(w)
      val resource = deserializeResource(x)
      val remaining = y.toInt
      val ttl = z.toLong
      RateLimit(token, resource, remaining, ttl)
  }

  private def deserializeAccess(s: String) = s.split(":") match {
    case Array(x, y) => AccessToken(x, y)
  }

  private def deserializeBearer(s: String) = BearerToken(s)

  private def deserializeConsumer(s: String) = s.split(":") match {
    case Array(x, y) => ConsumerToken(x, y)
  }

  private def deserializeUnavailable(s: String) = s.split(":") match {
    case Array(x, y) =>
      val resource = deserializeResource(x)
      val ttl = y.toLong
      Unavailable(resource, ttl)
  }

  private def deserializeResource(s: String) = s match {
    case "ULOOKUP" => UsersLookup
    case "USHOW" => UsersShow
    case "SLOOKUP" => StatusesLookup
    case "SSHOW" => StatusesShow
    case "SUSERTIMELINE" => StatusesUserTimeline
    case "FRIDS" => FriendsIds
    case "FRLIST" => FriendsList
    case "FOIDS" => FollowersIds
    case "FOLIST" => FollowersList
  }

}