package xkcd1083

import akka.actor._
import akka.dispatch.Future
import akka.routing.RoundRobinRouter

object FollowerActors extends NicksReqToq {

  class FriendGetter extends Actor {
    import Friends._
    
    def receive = {
      case Work(screen_name) => {
        val sender = this.sender
        val future = TweetIO.friends(screen_name)
        future onSuccess {
          case fr => sender ! Return(screen_name, fr.ids)
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
          case thr => thr match {
            case RateLimitedResponse(timeoutOpt) =>
              sender ! RateLimitWarning(timeoutOpt)
            case _ =>
              println(thr.getMessage)
              sender ! HttpErr(thr.getMessage)
          }
        }
    }
  }

  class FollowFinder extends Actor {
    import WhoToFollow._ 
    val officeTitles = List(
      "President",
      "Governor",
      "Mayor",
      "Senator",
      "Congressman",
      "Congresswoman",
      "Representative",
      "Senator",
      "Prime Minister",
      "Parliament"
    ).map{ _.r }

    def shouldFollow(person: Twitterer): Boolean = 
      officeTitles.exists {
        ! _.findFirstIn(person.description).isEmpty
      } && 
      person.followers_count >= 25000 &&
      person.verified

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
      case Work(person) =>
        val sender = this.sender
        val future = TweetIO.follow(person)
        future onSuccess {
          case _ => sender ! Return(person.screen_name)
        }
        /*for (thr <- promise.left)
          sender ! HttpErr(thr.getMessage)*/
    }
  }

  sealed trait FollowerTask
  sealed trait FollowerMessage
  type FMsg = FollowerMessage
  object Friends extends FollowerTask {
    case class Work(screen_name: String) extends FMsg
    case class Return(screen_name: String, ids: List[String]) extends FMsg
    case class RateLimitWarning(timeoutOpt: Option[Int]) extends FMsg
    case class HttpErr(errmsg: String) extends FMsg
  }
  object FriendInfo extends FollowerTask {
    case class Work(screen_name: String, ids: List[String]) extends FMsg
    case class Return(screen_name: String, people: List[Twitterer]) extends FMsg
    case class RateLimitWarning(timeoutOpt: Option[Int]) extends FMsg
    case class HttpErr(errmsg: String) extends FMsg
  }
  object WhoToFollow extends FollowerTask {
    case class Work(screen_name: String, people: List[Twitterer]) extends FMsg
    case class Return(screen_name: String, people: List[Twitterer]) extends FMsg
  }
  object FollowHim extends FollowerTask {
    case class Work(person: Twitterer) extends FMsg 
    case class Return(screen_name: String) extends FMsg
    case class HttpErr(errmsg: String) extends FMsg
  }

}
