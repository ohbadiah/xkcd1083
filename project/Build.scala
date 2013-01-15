import sbt._
import Keys._

object XkcdBuild extends Build {

  lazy val root = Project(
    id = "xkcd1083", 
    base = file(".")
  ).settings(
      version := "0.1",
      organization := "me.thefalcon",
      name := "xkcd1083",
      scalaVersion := "2.10.0",
      libraryDependencies ++= Seq(
        "org.clapper" % "grizzled-slf4j_2.10" % "1.0.1",
        "org.clapper" % "grizzled-scala_2.10" % "1.1.2",
        "net.databinder.dispatch" %% "dispatch-core" % "0.9.5",
        "io.spray" %%  "spray-json" % "1.2.3", 
        "com.typesafe.akka" %% "akka-actor" % "2.1.0",
        "com.typesafe.akka" %% "akka-testkit" % "2.1.0" % "test", 
        "org.scalatest" %% "scalatest" % "1.9.1" % "test"
      ),
      resolvers ++= Seq(
        "Sonatype latest" at 
          "https://oss.sonatype.org/service/local/repositories/releases/content/",
        "Typesafe Repository" at 
          "http://repo.typesafe.com/typesafe/releases/",
        "Spray Repository" at 
          "http://repo.spray.io/"),
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")
  )
}
