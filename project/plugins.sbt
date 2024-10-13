// logLevel := Level.Warn

resolvers ++= Seq(
  Resolver.file("file", new File(Path.userHome.absolutePath + "/.ivy2/local/"))
)
resolvers += Classpaths.sbtPluginReleases

// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.9.4")

addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.10.0")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.1.0")

addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.3.11")