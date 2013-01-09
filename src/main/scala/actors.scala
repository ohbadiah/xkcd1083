package xkcd1083

import grizzled.slf4j.Logging

import akka.actor.{Actor, ActorRef, Props, FSM}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import dispatch.StatusCode

import scala.collection.immutable.Queue

object FollowerTrawler extends NicksReqToq {
    
  class PeopleMachine extends Actor with FSM[PMState, PMData] with Logging {
    import PeopleMachine._

    val friendGetter = context.actorOf(Props[FriendGetter])
    val friendInfo = context.actorOf(Props[FriendInfoGetter])

    startWith(Uninitialized, Empty)

    when(Uninitialized) {
      case Event(Start, Empty) =>
        TweetIO.friends() onSuccess { case frResp =>
          self ! QueueUp(frResp.ids)
        }
        stay using HaveSender(sender)

      case Event(QueueUp(ids), HaveSender(ref)) =>
        val (current_user, q) = (Queue.empty enqueue ids).dequeue
        self ! Start
        info("Uninitialized -> GettingIds")
        goto(GettingIds) using HaveUser(ref, Cursor(current_user, "-1"), q)
    }

    when(GettingIds) {
      case Event(Start, data@HaveUser(_, Cursor(id, cursor_str), _)) =>
        friendGetter ! Friends.Work(id, cursor_str)
        stay using data

      case Event(Continue, HaveUser(ref, Cursor(_, "0"), q)) =>
        if (q.isEmpty) {
          info("GettingIds -> Uninitialized (Finished!)")
          goto(Uninitialized) using HaveSender(ref)
        }
        else {
          val (front, back) = q.dequeue
          self ! Continue
          stay using HaveUser(ref, Cursor(front, "-1"), back)
        }
  
      case Event(Continue, HaveUser(ref, Cursor(id, cursor_str), q)) =>
        friendGetter ! Friends.Work(id, cursor_str)
        stay using HaveUser(ref, Cursor(id, "-1"), q)
    
      case Event(Friends.Return(user_id, cursor_str, Nil), data: HaveUser) =>
        self ! Continue
        stay using data.withCursor(Cursor(user_id, cursor_str))
 
      case Event(Friends.Return(user_id, next_cursor_str, ids), 
        HaveUser(ref, _, q)) =>
        val status = Cursor(user_id, next_cursor_str)
        val jobs = Queue.empty ++ ids.grouped(100).map{ //NM Config
          FriendInfo.Work(user_id, _)
        }
        self ! Start
        info("GettingIds -> PopulatingIds")
        goto(PopulatingIds) using 
          HaveJobs(HaveUser(ref, status, q), jobs) 

      case Event(Friends.RateLimited(limitReset, job), data: HaveUser) =>
        info("FriendGetter rate limited for " + limitReset + "seconds.")
        context.system.scheduler.scheduleOnce(limitReset.seconds) {
          friendGetter ! job 
        }
        stay using data

      case Event(Friends.HttpErr(code, job), data: HaveUser) =>
        info("FriendGetter HTTP " + code)
        friendGetter ! job 
        stay using data
    }

    when(PopulatingIds) {
      case Event(Start, HaveJobs(user, jobs)) if jobs.isEmpty => 
        self ! Continue
        info("PopulatingIds -> GettingIds")
        goto(GettingIds) using user

      case Event(Start, HaveJobs(user, jobs)) => 
        val (front, back) = jobs.dequeue
        friendInfo ! front
        stay using HaveJobs(user, back)

      case Event(ret: FriendInfo.Return, HaveJobs(user, jobs)) =>
        user.ref ! ret
        if (jobs.isEmpty) {
          info("PopulatingIds -> GettingIds")
          self ! Continue
          goto(GettingIds) using user
        }
        else {
          val (front, back) = jobs.dequeue
          friendInfo ! front
          stay using HaveJobs(user, back)
        }
      
      case Event(FriendInfo.RateLimited(limitReset, job), data: HaveJobs) => 
        info("FriendInfo rate limited for " + limitReset + " seconds.")
        context.system.scheduler.scheduleOnce(limitReset.seconds) {
          friendInfo ! job
        }
        stay using data

      case Event(FriendInfo.HttpErr(code, job), data: HaveJobs) =>
        info("FriendInfo HTTP " + code)
        friendInfo ! job
        stay using data 
    }
  }

  class TrawlerSupervisor extends Actor with Logging {
    import TrawlerSupervisor._

    val peopleMachine = context.actorOf(Props[PeopleMachine])
    val followFinder = context.actorOf(Props[FollowFinder])
    val follower = context.actorOf(Props[Follower])

    def receive = {
      case Start =>
        peopleMachine ! Start

      case FriendInfo.Return(user_id, people) =>
        info("Supervisor got back " + people.size + " people.")
        followFinder ! FollowFinder.Work(user_id, people)

      case FollowFinder.Return(user_id, people@_::_) =>
        people foreach {tw: Twitterer =>  
          follower ! Follower.Work(tw.id_str) 
        }

      case Follower.Return(person) => 
        info("Followed " + person)
       
      case Follower.HttpErr(code, job) =>
        follower ! job
    }
  }

  class FriendGetter extends Actor {
    import Friends._
    
    def receive = {
      case job@Work(user_id, next_cursor_str) => {
        val sender = this.sender
        val future = TweetIO.friends(user_id, next_cursor_str)
        future onSuccess {
          case fr => sender ! Return(user_id, fr.next_cursor_str, fr.ids)
        }
        future onFailure {
          case rte: RuntimeException => rte.getCause match {
            case r@RateLimitedResponse(_) =>
              sender ! RateLimited(r.limitReset, job) 
            case StatusCode(code) =>
              sender ! HttpErr(code, job)
            }
          case thr => ()
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
        future onFailure { 
          case rte: RuntimeException => rte.getCause match {
            case StatusCode(code) => sender ! HttpErr(code, job)
            case r@RateLimitedResponse(_) => 
              sender ! RateLimited(r.limitReset, job)
            }
          case thr => ()
        }
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
      "MP",
      "Secretary"
    )

    private[this] val foils = Set(
      "correspondent",
      "journalist",
      "host",
      "report",
      "reporter",
      "anchor",
      "contributor",
      "press",
      "correspondent",
      "ceo",
      "adviser",
      "singer"
    )

    private[this] def shouldFollow(person: Twitterer): Boolean = {
      lazy val wordSet = person.description.getOrElse("").filter{ c: Char => 
        c.isLetter || c.isWhitespace 
      }.split(" ").toSet
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
        future onFailure { 
          case rte: RuntimeException => rte.getCause match {
            case StatusCode(code) =>
              sender ! HttpErr(code, job)
            }
          case thr => ()
            
        }
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
    case class RateLimited(timeoutOpt: Int, job: Work) extends FMsg
    case class HttpErr(statusCode: Int, job: Work) extends FMsg
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
    case object TrawlNext extends FMsg
  }


  sealed trait PMState
  sealed trait PMData
  object PeopleMachine {
    case object Continue extends FMsg
    case class QueueUp(ids: List[String]) extends FMsg

    case object Uninitialized extends PMState
    case object GettingIds extends PMState
    case object PopulatingIds extends PMState
    case object Inconsistent extends PMState
    
    case object Empty extends PMData

    case class Cursor(
      user_id: String, 
      next_cursor_str: String
    ) extends PMData
    case class HaveSender(ref: ActorRef) extends PMData

    case class HaveUser(
      ref: ActorRef, 
      current: Cursor, 
      backlog: Queue[String]
    ) extends PMData {
      def withCursor(other: Cursor) = HaveUser(ref, other, backlog)
    }

    case class HaveJobs(
      user: HaveUser,
      jobs: Queue[FriendInfo.Work]
    ) extends PMData
  }
}
