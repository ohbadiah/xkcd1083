package xkcd1083

import spray.json.DefaultJsonProtocol

case class Tweet(
  user: Twitterer,
  text: String
)

object Tweet

case class Twitterer(
  id_str: String,
  name: String,
  screen_name: String,
  location: String,
  description: String,
  followers_count: Long,
  verified: Boolean
)
object Twitterer

case class FriendResponse(
  previous_cursor_str: String,
  next_cursor_str: String,
  ids: List[String]
)
object FriendResponse


object TwitterJsonProtocol extends DefaultJsonProtocol {
  implicit val twittererFormat = jsonFormat7(Twitterer.apply)
  implicit val tweetFormat = jsonFormat2(Tweet.apply)
  implicit val friendResponseFormat = jsonFormat3(FriendResponse.apply)
}

