package me.thefalcon.xkcd1083

import dispatch._
import oauth._

import com.ning.http.client
import client.oauth.{RequestToken,ConsumerKey}

import cc.spray.json._
import TwitterJsonProtocol._


class TweetIO(accessToken: RequestToken) extends Xkcd1083Consumer {
  type DispatchResult[T] = Promise[Either[String, T]]
  
  def timeline: DispatchResult[List[Tweet]] = 
    oauthRequest[List[Tweet]](
      twitterApi / "statuses" / "home_timeline.json",
      Map(
        "count" -> "10",
        "include_entities" -> "false"
      )
    )

  def friends(screen_name: String = "xkcd1083"): DispatchResult[List[String]] = 
    oauthRequest[FriendResponse](
      twitterApi / "friends" / "ids.json",
      Map(
        "screen_name" -> screen_name,
        "stringify_ids" -> "true"
      )
    ).right.map{ _.ids }

  def users(ids: List[String]): DispatchResult[List[Twitterer]] = 
    oauthRequest[List[Twitterer]](
      twitterApi / "users" / "lookup.json",
      Map(
        "ids" -> ids.mkString(","),
        "include_entities" -> "false"
      ),
      isGet = false
    )
      
  def oauthRequest[T: JsonReader](
    target: client.RequestBuilder,
    params: Traversable[(String, String)],
    isGet: Boolean = true
  ): DispatchResult[T] = {
    val bareRequest = if (isGet) target <<? params else target << params
    Http(
      bareRequest <@ (consumer, accessToken) OK 
        { _.getResponseBody.asJson.convertTo[T] }
    ).either.left.map{ 
      _.getMessage
    }.right.map { identity }
  }
  
  def twitterApi =
    host("api.twitter.com").secure / "1.1"
}

object BugHandler extends (client.Response => String) {
  def apply(r: client.Response) =
    r.getStatusCode + ": " + r.getResponseBody
}
