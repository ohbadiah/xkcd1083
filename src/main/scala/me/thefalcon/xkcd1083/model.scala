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


object TwitterJsonProtocol extends DefaultJsonProtocol {
  implicit val twittererFormat = jsonFormat5(Twitterer.apply)
  implicit val tweetFormat = jsonFormat2(Tweet.apply)
}

