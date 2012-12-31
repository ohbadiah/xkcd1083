package me.thefalcon.xkcd1083

import dispatch._
import oauth._

import com.ning.http.client
import client.{Response, AsyncCompletionHandler, RequestBuilder}
import client.oauth.{RequestToken,ConsumerKey}

import spray.json._
import TwitterJsonProtocol._

import akka.dispatch.{ExecutionContext, Future}

object TweetIO extends Xkcd1083Consumer with NicksReqToq {
  import PromiseImplicits._
  implicit def ec: ExecutionContext = 
    ExecutionContext.fromExecutor(Http.promiseExecutor)
  
  def timeline: Future[List[Tweet]] = 
    oauthRequest[List[Tweet]](
      twitterApi / "statuses" / "home_timeline.json",
      Map(
        "count" -> "10",
        "include_entities" -> "false"
      )
    )

  def friends(screen_name: String = "xkcd1083"): Future[FriendResponse] = 
    oauthRequest[FriendResponse](
      twitterApi / "friends" / "ids.json",
      Map(
        "screen_name" -> screen_name,
        "stringify_ids" -> "true"
      )
    )

  def users(ids: List[String]): Future[List[Twitterer]] = 
    oauthRequest[List[Twitterer]](
      twitterApi / "users" / "lookup.json",
      Map(
        "user_id" -> ids.mkString(","),
        "include_entities" -> "false"
      ),
      isGet = false
    )

  def follow(person: Twitterer): Future[Twitterer] = 
    oauthRequest[Twitterer](
      twitterApi / "friendships" / "create.json",
      Map(
        "user_id" -> person.id_str,
        "screen_name" -> person.screen_name,
        "follow" -> "true"
      ),
      isGet = false
    )

      
  def oauthRequest[T: JsonReader](
    target: RequestBuilder,
    params: Traversable[(String, String)],
    isGet: Boolean = true
  ): Future[T] = {
    val bareRequest = if (isGet) target <<? params else target << params
    Http(
      ((bareRequest <@ (consumer, tok)).build(), 
      new RateLimitHandler[T]({_.getResponseBody.asJson.convertTo[T]})
      )
    ).either
  }
  
  def twitterApi =
    host("api.twitter.com").secure / "1.1"
}

class RateLimitHandler[T](f: Response => T) extends AsyncCompletionHandler[T] {
    def onCompleted(r: Response) = {
      val code: Int = r.getStatusCode
      if (code / 100 == 2)  
        f(r) 
      else if (code == 429)
        throw RateLimitedResponse(r)
      else throw StatusCode(r.getStatusCode)
    }
}

case class RateLimitedResponse(
  limitResetOption: Option[Int]
) extends Exception {
  val defaultTimeoutSecs: Int = 60
  def limitReset: Int = limitResetOption.getOrElse(defaultTimeoutSecs)
}
object RateLimitedResponse {
  import scala.collection.JavaConversions.collectionAsScalaIterable
  def apply[T](r: Response): RateLimitedResponse = {
  RateLimitedResponse(
      r.getHeaders("X-Rate-Limit-Reset").headOption.map{ _.toInt }
    )
  }
}
