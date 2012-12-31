package me.thefalcon.xkcd1083

import grizzled.slf4j.Logging
import dispatch._

object Main extends Logging 
  with NicksReqToq with App {
  override def main(args: Array[String]) = {
    val future = TweetIO.timeline
    future onSuccess {
      case list =>
        list foreach { tw: Tweet =>  
          info("@" + tw.user.screen_name + ": " + tw.text)
        }
        Http.shutdown
    }
  }
}
