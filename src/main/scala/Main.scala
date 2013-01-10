package xkcd1083

import grizzled.slf4j.Logging
import dispatch.Http

import akka.actor.{ActorSystem, Props}

object Main extends Logging with App {
  override def main(args: Array[String]) = {
      import FollowerTrawler.{TrawlerSupervisor, Start}
      val system = ActorSystem("TrawlerSystem")
      val sup = system.actorOf(
        Props[TrawlerSupervisor],
        name="Supervisor"
      )
      sup ! Start
  }
}
