import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "jury"
  val appVersion      = "1.0-SNAPSHOT"

  lazy val scalikejdbcVersion =  "2.0.0"
  lazy val h2Version = "1.3.172"

  resolvers := Seq("typesafe" at "http://repo.typesafe.com/typesafe/releases/")
//  resolvers += Resolver.sonatypeRepo("snapshots")
//  resolvers += Resolver.url("play-plugin-releases", new URL("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns),

  val appDependencies = Seq(
    // Add your project dependencies here,
    jdbc,
    cache,
//    anorm,
//    ,
//   "org.scala-lang" %% "scala-pickling" % "0.8.0-SNAPSHOT"
    "org.scalikejdbc" %% "scalikejdbc"                     % scalikejdbcVersion,
    "org.scalikejdbc" %% "scalikejdbc-config"  % scalikejdbcVersion,
    "org.scalikejdbc" %% "scalikejdbc-play-plugin"           % "2.2.0",
    "org.scalikejdbc" %% "scalikejdbc-play-fixture-plugin"   % "2.2.0", // optional
   "org.postgresql"     %  "postgresql"                % "9.2-1003-jdbc4", // your JDBC driver
   "org.slf4j"          %  "slf4j-simple"              % "[1.7,)",          // slf4j implementation
    "org.apache.commons" % "commons-email" % "1.3.2",
  "org.specs2"           %% "specs2"                          % "2.1"               % "test"

  )


  val main = play.Project(appName, appVersion, appDependencies).settings(
    // Add your own project settings here      
  )

}
