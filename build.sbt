import play.PlayImport._
import play.PlayScala

name         := "jury"

version      := "1.0-SNAPSHOT"

val scalikejdbcVersion =  "2.2.1"

resolvers += Resolver.url("Edulify Repository", url("http://edulify.github.io/modules/releases/"))(Resolver.ivyStylePatterns)


libraryDependencies ++= Seq(
  "com.mohiva" %% "play-silhouette" % "1.0",
  "org.webjars" %% "webjars-play" % "2.3.0",
  "org.webjars" % "bootstrap" % "3.1.1",
  "org.webjars" % "jquery" % "1.11.0",
  "net.codingwell" %% "scala-guice" % "4.0.0-beta4",
  "com.typesafe.play" %% "play-slick" % "0.8.0",
  "com.edulify" %% "play-hikaricp" % "1.5.1",
  "mysql" % "mysql-connector-java" % "5.1.32",
  "org.scalikejdbc" %% "scalikejdbc"                     % scalikejdbcVersion,
  "org.scalikejdbc" %% "scalikejdbc-config"  % scalikejdbcVersion,
  "org.scalikejdbc" %% "scalikejdbc-play-plugin"           % "2.3.4",
  "org.scalikejdbc" %% "scalikejdbc-play-fixture-plugin"   % "2.3.4", // optional
  "org.slf4j"          %  "slf4j-simple"              % "[1.7,)",
  "org.apache.commons" % "commons-email" % "1.3.2",
  "org.intracer" %% "mwbot" % "0.2.1",
  "org.webjars" % "angularjs" % "1.1.5-1",
  "nl.grons" %% "metrics-scala" % "3.3.0_a2.2",
  "org.specs2"           %% "specs2"                          % "2.4.2"               % "test",
  jdbc, cache, filters
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)