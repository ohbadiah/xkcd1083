package me.thefalcon.xkcd1083

import dispatch._
import oauth._

import com.ning.http.client
import client.oauth.{RequestToken,ConsumerKey}

import cc.spray.json._
import TwitterJsonProtocol._

object TweetIO extends Xkcd1083Consumer {
  def apply(accessToken: RequestToken): Promise[Either[String,List[Tweet]]] = {
    Http( 
      twitterApi / "statuses" / "home_timeline.json" <<? List(
        ("count", "10"),
        ("include_entities", "false")
      ) <@ (consumer, accessToken)
      OK TweetHandler
    ).either.left.map {
      _.getMessage
    }.right.map { identity }
  }

  def twitterApi =
    host("api.twitter.com").secure / "1.1"
}

object TweetHandler extends (client.Response => List[Tweet]) {
  def apply(r: client.Response) = 
    r.getResponseBody.asJson.convertTo[List[Tweet]]
}

object BugHandler extends (client.Response => String) {
  def apply(r: client.Response) =
    r.getStatusCode + ": " + r.getResponseBody
}
