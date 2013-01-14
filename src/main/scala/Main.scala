package xkcd1083

import grizzled.slf4j.Logging

import akka.actor.{ActorSystem, Props}

/** Starts the FollowerTrawler. */
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
