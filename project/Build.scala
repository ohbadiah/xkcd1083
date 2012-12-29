import sbt._
import Keys._
import sbtassembly.Plugin._
import AssemblyKeys._


object XkcdBuild extends Build {
  System.setProperty("org.clapper.avsl.config", "avsl.conf")

  lazy val root = Project(
    id = "xkcd1083", 
    base = file("."),
    settings = Project.defaultSettings ++ assemblySettings
  ).settings(
      version := "0.1",
      organization := "me.thefalcon",
      name := "xkcd1083",
      scalaVersion := "2.9.2",
      libraryDependencies ++= Seq(
        "net.databinder.dispatch" %% "core" % "0.9.1",
        "io.spray" %%  "spray-json" % "1.2.2" cross CrossVersion.full,
        "org.clapper" %% "grizzled-slf4j" % "0.6.9",
        "com.typesafe.akka" % "akka-actor" % "2.0.3",
        "com.typesafe.akka" % "akka-testkit" % "2.0.3" % "test",
        "org.clapper" %% "avsl" % "0.4",
        "org.scalatest" %% "scalatest" % "1.8" % "test"
      ),
      resolvers ++= Seq(
        "Sonatype latest" at 
          "https://oss.sonatype.org/service/local/repositories/releases/content/",
        "Typesafe Repository" at 
          "http://repo.typesafe.com/typesafe/releases/")
  )
}
