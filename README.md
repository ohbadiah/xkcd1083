This Scala project was inspired by xkcd.com/1083, a comic that insinuates many politicians unnecessarily use teenager-style abbreviations on twitter. The idea is to create a program that follows lots of prominent politicians and calls them out by retweeting or replying.

Included so far is only the following engine. Given a seed of a few accounts to follow, the bot traverses the lists of accounts THOSE people follow, follows those accounts it deems suitable, and repeats. A preliminary run with the account [@xkcd1083](https://twitter.com/xkcd1083) produced approximately 140 politicians of note around the world within a couple of hours. The current process is very simple and much improvement could be made with e.g. the addition of persistent search state or methods other than straight depth-first search of followed accounts. False positives are also a problem and must be manually pruned.

I use [Dispatch](http://dispatch.databinder.net) for HTTP interaction and [Akka](http://akka.io) for concurrent composition of twitter API operations, along with [spray-json](https://github.com/spray/spray-json) for JSON parsing, [Scalatest](http://scalatest.org) for unit tests, and [grizzled-scala](http://software.clapper.org/grizzled-scala) and [grizzled-SLF4J](http://software.clapper.org/grizzled-slf4j) for configuration and logging, respectively.

Early inspection revealed that the alleged linguistic infringement occurs rarely if at all, at least on official accounts, and so I may not be very inspired to build the tweeting portion of the bot. Traversing the follower graph is fun, though, and the code here could very easily be adapted to similar tasks.