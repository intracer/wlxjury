import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "jury"
  val appVersion      = "1.0-SNAPSHOT"

  lazy val scalikejdbcVersion = "1.6.5"
  lazy val h2Version = "1.3.172"

  resolvers += Resolver.sonatypeRepo("snapshots")

  val appDependencies = Seq(
    // Add your project dependencies here,
    jdbc,
//    anorm,
//    ,
//   "org.scala-lang" %% "scala-pickling" % "0.8.0-SNAPSHOT"
  "com.github.seratch" %% "scalikejdbc"               % "[1.6,)",
   "com.github.seratch" %% "scalikejdbc-interpolation" % "[1.6,)",
   "org.postgresql"     %  "postgresql"                % "9.2-1003-jdbc4", // your JDBC driver
   "org.slf4j"          %  "slf4j-simple"              % "[1.7,)"          // slf4j implementation
  )


  val main = play.Project(appName, appVersion, appDependencies).settings(
    // Add your own project settings here      
  )

}
