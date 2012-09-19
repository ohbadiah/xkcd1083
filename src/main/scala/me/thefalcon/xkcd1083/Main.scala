package me.thefalcon.xkcd1083

import dispatch._

object Main extends Object 
  with NicksReqToq {
  def main(args: Array[String]) = {
   val promise = TweetIO(tok)
   for (
     list <- promise.right;
     tweet <- list
   ) {println("@" + tweet.user.screen_name + ": " + tweet.text)}

  }
}
