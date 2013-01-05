package xkcd1083.test

import org.scalatest.FunSuite
import akka.actor._
import akka.util.duration._
import akka.dispatch.{Await, Future}
import akka.testkit.TestActorRef
import akka.pattern.ask

import xkcd1083._

class TwitterActorSuite extends FunSuite {
  implicit val system = ActorSystem("TestSystem")
  implicit val timeout = akka.util.Timeout(3 seconds)
  import FollowerTrawler._

  test("We follow people.") {
    val actorRef = TestActorRef[FriendGetter]
    val result = Await.result(
      (actorRef ? Friends.Work("xkcd1083", "-1")), timeout.duration)
        .asInstanceOf[Friends.Return]
    assert(! result.ids.isEmpty)
  }

  test("Barack Obama's account is verified.") {
    val actorRef = TestActorRef[FriendInfoGetter]
    val ids = List("16789970", "90484508")
    val result = Await.result(
      (actorRef ? FriendInfo.Work("xkcd1083", ids)), timeout.duration)
        .asInstanceOf[FriendInfo.Return]
    assert(! result.people.isEmpty && result.people.forall{ _.verified })
  }

  test("We think we should follow everyone we follow.") {
    val future = for {
      friend_ids <- TweetIO.friends("xkcd1083")
      friends  <- TweetIO.users(friend_ids.ids)
      shouldFollow <- TestActorRef[FollowFinder] ? FollowFinder.Work("xkcd1083", friends) 
    } yield (friends, shouldFollow)
    val (l1, ret) = Await.result(future, timeout.duration)
      .asInstanceOf[(List[Twitterer], FollowFinder.Return)]
    assert((l1 filterNot (ret.people contains)) isEmpty)
  }

  test("We already follow Barack Obama.") {
    val result = Await.result(
      TestActorRef[Follower] ? Follower.Work("813286"), timeout.duration
    ).asInstanceOf[Follower.Return]
    assert(result.person.verified)

  }
}

