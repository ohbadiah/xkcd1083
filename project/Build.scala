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
      libraryDependencies ++= Seq(
        "net.databinder.dispatch" %% "core" % "0.9.1",
        "cc.spray" %% "spray-json" % "1.1.1",
        "org.clapper" %% "grizzled-slf4j" % "0.6.9",
        "org.clapper" %% "avsl" % "0.4"
      ),
      resolvers += ("Sonatype latest" at 
        "https://oss.sonatype.org/service/local/repositories/releases/content/")
  )
}
