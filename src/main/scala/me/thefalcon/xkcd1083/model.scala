package me.thefalcon.xkcd1083

import cc.spray.json.DefaultJsonProtocol

case class Tweet(
  user: Twitterer,
  text: String
)

object Tweet

case class Twitterer(
  id: Int,
  name: String,
  screen_name: String,
  location: String,
  description: String
)
object Twitterer

case class FriendResponse(
  previous_cursor: Int,
  next_cursor: Int,
  ids: List[String]
)
object FriendResponse


object TwitterJsonProtocol extends DefaultJsonProtocol {
  implicit val twittererFormat = jsonFormat5(Twitterer.apply)
  implicit val tweetFormat = jsonFormat2(Tweet.apply)
  implicit val friendResponseFormat = jsonFormat3(FriendResponse.apply)
}

