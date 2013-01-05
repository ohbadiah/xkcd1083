package xkcd1083

import grizzled.slf4j.Logging

import akka.actor._
import scala.concurrent.Future
import akka.routing.RoundRobinRouter
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import dispatch.StatusCode

import scala.collection.mutable.Queue

object FollowerTrawler extends NicksReqToq {

  class TrawlerSupervisor extends Actor with Logging {
    import TrawlerSupervisor._

    val friendQueue: Queue[String] = Queue.empty
    val infoQueue: Queue[FriendInfo.Work] = Queue.empty
    val followQueue: Queue[Follower.Work] = Queue.empty
    var next_cursor_str = "-1"
    val friendGetterRouter = context.actorOf(Props[FriendGetter]
      withRouter(RoundRobinRouter(1)), name="FriendGetterRouter")
    val friendInfoRouter = context.actorOf(Props[FriendInfoGetter]
      withRouter(RoundRobinRouter(2)), name="FriendInfoRouter")
    val followFinderRouter = context.actorOf(Props[FollowFinder]
      withRouter(RoundRobinRouter(2)), name="FollowFinder")
    val followerRouter = context.actorOf(Props[Follower]
      withRouter(RoundRobinRouter(2)), name="Follower")

    def receive = {
      case Start =>
        info("Starting.")
        if (friendQueue.isEmpty) 
          TweetIO.friends() onSuccess { case frResp =>
            info("Telling self to queue " + frResp.ids.size + " ids.")
            self ! QueueUp(frResp.ids)
          }
        else friendGetterRouter ! 
          Friends.Work(friendQueue.front, next_cursor_str)

      case QueueUp(ids) =>
        info("Received QueueUp.")
        friendQueue ++= ids
        if (! friendQueue.isEmpty) self ! TrawlNext

      case TrawlNext =>
        info("Received TrawlNext.")
        next_cursor_str = "-1"
        friendGetterRouter ! Friends.Work(friendQueue.front, next_cursor_str)

      case Friends.Return(user_id, cursor_str, ids) =>
        info("Got back " + ids.size + " followers of " + user_id)
        next_cursor_str = cursor_str
        if (next_cursor_str == "0") {
          info("Got to end of " + user_id + "'s followers!")
          friendQueue.dequeue
          self ! TrawlNext
        }
        val wasEmpty = infoQueue.isEmpty
        infoQueue ++= ids.grouped(100) map { //NM config
          FriendInfo.Work(user_id, _)
        }
        friendInfoRouter ! infoQueue.dequeue

      case Friends.RateLimited(limitReset, cursor_str) =>
        info("Friends rate limited with " + limitReset + " seconds left.")
        next_cursor_str = cursor_str
        val front = friendQueue.front
        context.system.scheduler.scheduleOnce(limitReset.seconds) {
          friendGetterRouter ! Friends.Work(front, next_cursor_str)
        }
     
      case Friends.HttpErr(code, cursor_str) =>
        info("HTTP Error " + code + " on friend request.")
        friendGetterRouter ! Friends.Work(friendQueue.front, next_cursor_str) 
      case FriendInfo.Return(user_id, people) =>
        info("FriendInfo returned with " + people.size + " people.")
        followFinderRouter ! FollowFinder.Work(user_id, people)
        if (infoQueue.isEmpty) {
          if (friendQueue.nonEmpty) 
            friendGetterRouter ! 
              Friends.Work(friendQueue.front, next_cursor_str) 
        } 
        else { friendInfoRouter ! infoQueue.dequeue }
      
      case FriendInfo.RateLimited(limitReset, job) => 
        info("FriendInfo rate limited with " + limitReset + " seconds left till reset.")
        infoQueue.enqueue(job)
        val nextJob = infoQueue.dequeue
        context.system.scheduler.scheduleOnce(limitReset.seconds) {
          friendInfoRouter ! nextJob
        }
  
      case FriendInfo.HttpErr(code, job) =>
        info("HTTP Error " + code + " on friend info request.")
        infoQueue.enqueue(job)
        friendInfoRouter ! infoQueue.dequeue

      case FollowFinder.Return(user_id, people@_::_) =>
        info("Got back " + people.size + " people to follow!")
        followQueue ++= people map 
          {tw: Twitterer =>  Follower.Work(tw.id_str) }
        followerRouter ! followQueue.dequeue  

      case Follower.Return(person) =>
        info("Followed " + person)
        if (! followQueue.isEmpty) followerRouter ! followQueue.dequeue
       
      case Follower.HttpErr(code, job) =>
        info("HTTP Error " + code + " when following.")
        followQueue.enqueue(job)
        followerRouter ! followQueue.dequeue 
    }
  }

  class FriendGetter extends Actor {
    import Friends._
    
    def receive = {
      case Work(user_id, next_cursor_str) => {
        val sender = this.sender
        val future = TweetIO.friends(user_id, next_cursor_str)
        future onSuccess {
          case fr => sender ! Return(user_id, fr.next_cursor_str, fr.ids)
        }
        future onFailure {
          case r: RateLimitedResponse =>
            sender ! RateLimited(r.limitReset, next_cursor_str) 
          case sc: StatusCode =>
            sender ! HttpErr(sc.code, next_cursor_str)
          case thr =>
            sys.error("Unhandled error! " + thr.getMessage)
        }
      }
    }
  }

  class FriendInfoGetter extends Actor {
    import FriendInfo._

    def receive = {
      case job@Work(screen_name, ids) => 
        val sender = this.sender
        val future = TweetIO.users(ids)
        future onSuccess {
          case friends => sender ! Return(screen_name, friends)
        }
        future onFailure { case thr => thr match {
          case r: RateLimitedResponse =>
            sender ! RateLimited(r.limitReset, job) 
          case sc: StatusCode =>
            sender ! HttpErr(sc.code, job)
          case thr =>
            sys.error("Unhandled error! " + thr.getMessage)
        }}
    }
  }

  class FollowFinder extends Actor {
    import FollowFinder._ 
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
      "Minister",
      "Parliament",
      "Secretary"
    )

    private[this] val foils = Set(
      "correspondent",
      "journalist",
      "host"
    )

    private[this] def shouldFollow(person: Twitterer): Boolean = {
      lazy val wordSet = person.description.getOrElse("").filter
        { _.isLetter }.split(" ").toSet
      person.verified &&
      person.followers_count >= 25000 &&
      (officeTitles & wordSet).size > 0  &&
      (foils & wordSet.map{_.toLowerCase}).size == 0
    }


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
    import Follower._

    def receive = {
      case job@Work(id_str) =>
        val sender = this.sender
        val future = TweetIO.follow(id_str)
        future onSuccess {
          case person => sender ! Return(person)
        }
        future onFailure { case thr => thr match {
          case StatusCode(code) =>
            sender ! HttpErr(code, job)
          case thr =>
            sys.error("Unhandled error! " + thr.getMessage)
        }}
    }
  }

  sealed trait FollowerMessage
  type FMsg = FollowerMessage
  case object Start extends FMsg
  object Friends {
    case class Work(user_id: String, next_cursor_str: String) extends FMsg
    case class Return(
      user_id: String, 
      next_cursor_str: String, 
      ids: List[String]
    ) extends FMsg
    case class RateLimited(timeoutOpt: Int, next_cursor_str: String) extends FMsg
    case class HttpErr(statusCode: Int, next_cursor_str: String) extends FMsg
  }
  object FriendInfo {
    case class Work(user_id: String, ids: List[String]) extends FMsg
    case class Return(user_id: String, people: List[Twitterer]) extends FMsg
    case class RateLimited(timeout: Int, job: Work) extends FMsg
    case class HttpErr(statusCode: Int, job: Work) extends FMsg
  }
  object FollowFinder {
    case class Work(user_id: String, people: List[Twitterer]) extends FMsg
    case class Return(user_id: String, people: List[Twitterer]) extends FMsg
  }
  object Follower {
    case class Work(id_str: String) extends FMsg 
    case class Return(person: Twitterer) extends FMsg
    case class HttpErr(statusCode: Int, job: Work) extends FMsg
  }
  object TrawlerSupervisor {
    case class QueueUp(ids: Traversable[String]) extends FMsg
    case object TrawlNext extends FMsg
  }

}
