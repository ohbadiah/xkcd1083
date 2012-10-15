package me.thefalcon.xkcd1083

import grizzled.slf4j.Logging
import dispatch._

object Main extends Logging 
  with NicksReqToq {
  def main(args: Array[String]) = {
   val promise = new TweetIO(tok).timeline
   for (
     list <- promise.right;
     tweet <- list
   ) {
     info("@" + tweet.user.screen_name + ": " + tweet.text)
   }

  }
}
