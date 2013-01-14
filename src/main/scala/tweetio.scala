package xkcd1083

import dispatch._
import oauth._

import com.ning.http.client
import client.{Response, AsyncCompletionHandler, RequestBuilder}
import client.oauth.{RequestToken,ConsumerKey}

import spray.json._
import TwitterJsonProtocol._

import scala.concurrent.{ExecutionContext, Future}

/** Contains methods for hitting the twitter API relevant to 
 *  traversing the follower graph. */
object TweetIO extends HasConsumerKey with HasAccessToken {
  import PromiseImplicits._
  
  /** Registers the Ning async http client with Akka. */
  implicit def ec: ExecutionContext = 
    ExecutionContext.fromExecutor(Http.promiseExecutor)
 
  /** Gets the authenticated user's timeline. */ 
  def timeline: Future[List[Tweet]] = 
    oauthRequest[List[Tweet]](
      twitterApi / "statuses" / "home_timeline.json",
      Map(
        "count" -> "10",
        "include_entities" -> "false"
      )
    )

  /** Pulls down 5000 account id's the given user follows. */
  def friends(
    user_id: String = "705215413", // @xkcd1083's id
    cursor_str: String = "-1" // -1 signifies beginning, 0 the end.
  ): Future[FriendResponse] = 
    oauthRequest[FriendResponse](
      twitterApi / "friends" / "ids.json",
      Map(
        "user_id" -> user_id,
        "cursor" -> cursor_str,
        "stringify_ids" -> "true"
      )
    )

  val maxUsersPerRequest = 100 

  /** Turns up to 100 user id's into Twitterer values. */
  def users(ids: List[String]): Future[List[Twitterer]] = 
    oauthRequest[List[Twitterer]](
      twitterApi / "users" / "lookup.json",
      Map(
        "user_id" -> ids.mkString(","),
        "include_entities" -> "false"
      ),
      isGet = false
    )

  /** Cause the authenticated user to start following the given user. */
  def follow(id_str: String): Future[Twitterer] = 
    oauthRequest[Twitterer](
      twitterApi / "friendships" / "create.json",
      Map(
        "user_id" -> id_str,
        "follow" -> "true"
      ),
      isGet = false
    )
     
  /** Generic method for carrying out oauth request. */ 
  def oauthRequest[T: JsonReader](
    target: RequestBuilder,
    params: Traversable[(String, String)],
    isGet: Boolean = true
  ): Future[T] = {
    val bareRequest = if (isGet) target <<? params else target << params
    Http(
      ((bareRequest <@ (consumerKey, accessToken)).build(), 
      new RateLimitHandler[T]({_.getResponseBody.asJson.convertTo[T]})
      )
    ).either
  }
  
  /** Represents the endpoint https://api.twitter.com/1.1 */ 
  def twitterApi =
    host("api.twitter.com").secure / "1.1"
}

/** Handles rate-limited (i.e. HTTP 429) responses specifically in addition to
 * generic handling for non-200 responses. */
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

/** The Exception representing a rate-limited response. */
case class RateLimitedResponse(
  limitResetOption: Option[Int]
) extends Exception {
  private[this] val now = (System.currentTimeMillis / 1000).toInt
  val defaultTimeoutSecs: Int = 60
  /** Find how many seconds from now until the rate limit window resets. */
  def limitReset: Int = limitResetOption map 
    {_ - now} getOrElse(defaultTimeoutSecs)
}
object RateLimitedResponse {
  import scala.collection.JavaConversions.collectionAsScalaIterable
  def apply[T](r: Response): RateLimitedResponse = {
  RateLimitedResponse(
      r.getHeaders("X-Rate-Limit-Reset").headOption.map{ _.toInt }
    )
  }
}
