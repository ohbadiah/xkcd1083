package xkcd1083

import akka.actor._
import akka.dispatch.Future
import akka.routing.RoundRobinRouter

object FollowerActors extends NicksReqToq {

  class FriendGetter extends Actor {
    import Friends._
    
    def receive = {
      case Work(screen_name, next_cursor_str) => {
        val sender = this.sender
        val future = TweetIO.friends(screen_name, next_cursor_str)
        future onSuccess {
          case fr => sender ! Return(screen_name, fr.ids)
        }
        future onFailure {
          case RateLimitedResponse(timeout) =>
            sender ! RateLimited(timeout) 
        }
      }
    }
  }

  class FriendInfoGetter extends Actor {
    import FriendInfo._

    def receive = {
      case Work(screen_name, ids) => 
        val sender = this.sender
        val future = TweetIO.users(ids)
        future onSuccess {
          case friends => sender ! Return(screen_name, friends)
        }
        future onFailure {
          case RateLimitedResponse(timeout) =>
            sender ! RateLimited(timeout) 
        }
    }
  }

  class FollowFinder extends Actor {
    import WhoToFollow._ 
    private[this] val officeTitles = Set(
      "President",
      "Governor",
      "Mayor",
      "Senator",
      "Senate",
      "House",
      "Congress",
      "Congressional",
      "Representative",
      "Senator",
      "Prime Minister",
      "Parliament"
    )

    private[this] def shouldFollow(person: Twitterer): Boolean = 
      person.verified &&
      person.followers_count >= 25000 &&
      (officeTitles & person.description.split(" ").toSet).size > 0

    def receive = {
      case Work(screen_name, people) =>
        val sender = this.sender
        val toFollow = people.filter { shouldFollow(_) }
        toFollow match {
          case Nil => ()
          case _  => sender ! Return(screen_name, toFollow)
        }
    }
  }

  class Follower extends Actor {
    import FollowHim._

    def receive = {
      case Work(id_str) =>
        val sender = this.sender
        val future = TweetIO.follow(id_str)
        future onSuccess {
          case person => sender ! Return(person)
        }
    }
  }

  sealed trait FollowerMessage
  type FMsg = FollowerMessage
  object Friends {
    case class Work(screen_name: String) extends FMsg
    case class Return(screen_name: String, ids: List[String]) extends FMsg
    case class RateLimited(timeoutOpt: Option[Int]) extends FMsg
  }
  object FriendInfo {
    case class Work(screen_name: String, ids: List[String]) extends FMsg
    case class Return(screen_name: String, people: List[Twitterer]) extends FMsg
    case class RateLimited(timeoutOpt: Option[Int]) extends FMsg
  }
  object WhoToFollow {
    case class Work(screen_name: String, people: List[Twitterer]) extends FMsg
    case class Return(screen_name: String, people: List[Twitterer]) extends FMsg
  }
  object FollowHim {
    case class Work(id_str: String) extends FMsg 
    case class Return(person: Twitterer) extends FMsg
  }

}
