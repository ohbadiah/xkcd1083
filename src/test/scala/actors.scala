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
  import FollowerActors._

  test("we follow people") {
    val actorRef = TestActorRef[FriendGetter]
    val result = Await.result(
      (actorRef ? Friends.Work("xkcd1083")), timeout.duration)
        .asInstanceOf[Friends.Return]
    assert(! result.ids.isEmpty)
  }

  test("Barack Obama's account is verified") {
    val actorRef = TestActorRef[FriendInfoGetter]
    val ids = List("16789970")
    val result = Await.result(
      (actorRef ? FriendInfo.Work("xkcd1083", ids)), timeout.duration)
        .asInstanceOf[FriendInfo.Return]
    assert(! result.people.isEmpty && result.people.head.verified)
  }

}

