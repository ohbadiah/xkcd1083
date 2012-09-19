name := "xkcd1083"

version := "0.1"

libraryDependencies ++= Seq(
  "net.databinder.dispatch" %% "core" % "0.9.1",
  "cc.spray" %%  "spray-json" % "1.1.1"
)

resolvers += ("Sonatype latest" at "https://oss.sonatype.org/service/local/repositories/releases/content/")
