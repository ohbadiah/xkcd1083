package me.thefalcon.xkcd1083

import grizzled.slf4j.Logging
import dispatch._

object Main extends Logging 
  with NicksReqToq {
  def main(args: Array[String]) = {
   val promise = TweetIO(tok)
   for (
     list <- promise.right;
     tweet <- list
   ) {
     info("tweet")
     println("@" + tweet.user.screen_name + ": " + tweet.text)
   }

  }
}
