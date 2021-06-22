// logLevel := Level.Warn

resolvers ++= Seq("typesafe" at "https://repo.typesafe.com/typesafe/releases/",
  Resolver.file("file", new File(Path.userHome.absolutePath + "/.ivy2/local/")),
  "Scalaz Bintray Repo" at "https://dl.bintray.com/scalaz/releases"
)
resolvers += "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
resolvers += "Sonatype OSS Release Repository" at "https://oss.sonatype.org/content/repositories/releases/"
resolvers += Resolver.url(
  "bintray-sbt-plugin-releases",
  url("https://dl.bintray.com/content/sbt/sbt-plugin-releases"))(
  Resolver.ivyStylePatterns)
resolvers += Classpaths.sbtPluginReleases

// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.24")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.8.1")


addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.2.2")