package xkcd1083.test

import org.scalatest.FunSuite
import akka.testkit.TestActorRef
import scala.concurrent.ExecutionContext.Implicits.global

import xkcd1083._

class FollowingSuite extends FunSuite {
  implicit val system = akka.actor.ActorSystem("TestSystem")

  val followFinder = TestActorRef[FollowerTrawler.FollowFinder].underlyingActor

  val linda = Twitterer(
    "65522706","Linda McMahon","Linda_McMahon",Some("CT"), 
    Some("Proven job creator running for US Senate to get Connecticut " + 
    "working again!"),38564,true
  )
  val grover = Twitterer(
    "16045956", "Grover Norquist", "GroverNorquist", 
    Some("Washington, D.C."), Some("President, Americans for Tax Reform"),
    43156, true
  )
  val major = Twitterer(
    "46176168","Major Garrett ","MajorCBS",Some("Washington, DC"),
    Some("Chief White House Correspondent CBS News and columnist for " +
    "National Journal"),79448,true
  )
  val amazon = Twitterer(
    "14740219","Amazon MP3","amazonmp3",Some("Seattle"),
    Some("The official Amazon MP3 twitter feed. Follow us to get editor " +      "picks, find out about deals on music, and learn more about Amazon " +       "Cloud Player."),1614715,true
  )
  
  def doNotFollowTest(tw: Twitterer) = 
   test("We don't want to follow " + tw.screen_name) {
     assert(! followFinder.shouldFollow(tw))
   }
 
  def hitFoilTest(tw: Twitterer) = 
    test(tw.screen_name + " hits foil words") {
    val twDesc = followFinder.descProcess(tw)
    val foils = followFinder.foils & twDesc.map{_.toLowerCase}
    assert(foils.size > 0)
  }
 
  def haveOfficeTest(tw: Twitterer) = 
    test(tw.screen_name + " has an office title") {
    val twDesc = followFinder.descProcess(tw)
    val offices = followFinder.officeTitles & twDesc
    assert(offices.size > 0)
  }

  val all = List(linda, grover, major, amazon)
  val officed = List(linda, grover, major)
  val foiled = officed
  
  all foreach {doNotFollowTest(_)}
  officed foreach {haveOfficeTest(_)}
  foiled foreach {hitFoilTest(_)}
}

