import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "jury"
  val appVersion      = "1.0-SNAPSHOT"

  lazy val scalikejdbcVersion =  "2.1.2"
  lazy val h2Version = "1.3.172"

  resolvers ++= Seq("typesafe" at "http://repo.typesafe.com/typesafe/releases/",
    Resolver.file("file",  new File(Path.userHome.absolutePath+"/.ivy2/local/")),
    "Scalaz Bintray Repo"  at "http://dl.bintray.com/scalaz/releases"
  )
//  resolvers += Resolver.sonatypeRepo("snapshots")
//  resolvers += Resolver.url("play-plugin-releases", new URL("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns),

  val appDependencies = Seq(
    // Add your project dependencies here,
    jdbc,
    cache,
    filters,
//    anorm,
//    ,
//   "org.scala-lang" %% "scala-pickling" % "0.8.0-SNAPSHOT"
    "org.scalikejdbc" %% "scalikejdbc"                     % scalikejdbcVersion,
    "org.scalikejdbc" %% "scalikejdbc-config"  % scalikejdbcVersion,
    "org.scalikejdbc" %% "scalikejdbc-play-plugin"           % "2.2.0",
    "org.scalikejdbc" %% "scalikejdbc-play-fixture-plugin"   % "2.2.0", // optional
   "org.postgresql"     %  "postgresql"                % "9.2-1003-jdbc4", // your JDBC driver
    "mysql" % "mysql-connector-java" % "5.1.31",
   "org.slf4j"          %  "slf4j-simple"              % "[1.7,)",          // slf4j implementation
    "org.apache.commons" % "commons-email" % "1.3.2",
    "org.intracer" %% "mwbot" % "0.2.0",
    //"io.dropwizard.metrics" % "metrics-core" % "3.1.0",
      "nl.grons" %% "metrics-scala" % "3.3.0_a2.2",
    "org.webjars" % "webjars-play_2.10" % "2.2.0",
    "org.webjars" % "angularjs" % "1.1.5-1",
    "org.webjars" % "bootstrap" % "2.3.2",
    //    "com.typesafe.play" %% "play-slick" % "0.6.0.1",
  "org.specs2"           %% "specs2"                          % "2.4.2"               % "test"

  )


  val main = play.Project(appName, appVersion, appDependencies).settings(
    net.virtualvoid.sbt.graph.Plugin.graphSettings: _*
  )

}
