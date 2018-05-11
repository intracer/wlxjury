// logLevel := Level.Warn

resolvers ++= Seq("typesafe" at "http://repo.typesafe.com/typesafe/releases/",
  Resolver.file("file", new File(Path.userHome.absolutePath + "/.ivy2/local/")),
  "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"
)
resolvers += "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
resolvers += "Sonatype OSS Release Repository" at "https://oss.sonatype.org/content/repositories/releases/"
resolvers += Resolver.url(
  "bintray-sbt-plugin-releases",
  url("http://dl.bintray.com/content/sbt/sbt-plugin-releases"))(
  Resolver.ivyStylePatterns)
resolvers += Classpaths.sbtPluginReleases

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.1.1")

// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.12")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.1.4")


addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.0.4")

addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.0.0.BETA1")