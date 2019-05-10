resolvers ++= Seq(
  "typesafe" at "http://repo.typesafe.com/typesafe/releases/",
  Resolver.file("file", new File(Path.userHome.absolutePath + "/.ivy2/local/")),
  "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",
  "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Sonatype OSS Release Repository" at "https://oss.sonatype.org/content/repositories/releases/",
  Resolver.url("bintray-sbt-plugin-releases", url("http://dl.bintray.com/content/sbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns),
  Classpaths.sbtPluginReleases
)

// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.15")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.6")

addSbtPlugin("io.gatling" % "gatling-sbt" % "2.2.2")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.2.2")