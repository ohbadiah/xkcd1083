package me.thefalcon.xkcd1083

import akka.actor._
import akka.routing.RoundRobinRouter

object FollowerActors extends NicksReqToq {
  def apiClient = new TweetIO(tok)

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

  class FriendGetter extends Actor {
    import Friends._
    
    def receive = {
      case Work(screen_name) =>
        val promise = apiClient.friends(screen_name)
        for (friendResponse <- promise.right) {
          sender ! Return(screen_name, friendResponse.ids)
        }
    }
  }

  class FriendInfoGetter extends Actor {
    import FriendInfo._

    def receive = {
      case Work(screen_name, ids) => 
        val promise = apiClient.users(ids)
        for (friends <- promise.right) {
          sender ! Return(screen_name, friends)
        }
        for (thr <- promise.left)
          thr match {
            case RateLimitedResponse(timeoutOpt) =>
              sender ! RateLimitWarning(timeoutOpt)
            case _ =>
              sender ! HttpErr(thr.getMessage)
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
        val promise = apiClient.follow(person)
        for (_ <- promise.right)
          sender ! Return(person.screen_name)
        for (thr <- promise.left)
          sender ! HttpErr(thr.getMessage)
    }
  }

}
