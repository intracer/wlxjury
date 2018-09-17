import play.sbt.PlayImport.{filters, guice, jdbc, specs2}
import sbt.{Resolver, _}

object Dependencies {
  val ScalikejdbcVersion = "3.2.2"
  val ScalikejdbcPlayVersion = "2.6.0-scalikejdbc-3.2"
  val ScalawikiVersion = "0.5.0"
  val PlayMailerVersion = "6.0.1"

  val resolvers = Seq(
    Resolver.bintrayRepo("intracer", "maven"),
    "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
    Resolver.jcenterRepo
  )

  val dependencies = Seq(
    "org.webjars" %% "webjars-play" % "2.6.3",
    "com.adrianhurt" %% "play-bootstrap" % "1.2-P26-B3",
    "org.webjars" % "bootstrap" % "3.3.7-1" exclude("org.webjars", "jquery"),
    "org.webjars" % "jquery" % "3.2.1",

    "mysql" % "mysql-connector-java" % "5.1.40",
    "org.scalikejdbc" %% "scalikejdbc" % ScalikejdbcVersion,
    "org.scalikejdbc" %% "scalikejdbc-config" % ScalikejdbcVersion,
    "org.scalikejdbc" %% "scalikejdbc-play-initializer" % ScalikejdbcPlayVersion,
    "org.scalikejdbc" %% "scalikejdbc-play-dbapi-adapter" % ScalikejdbcPlayVersion,
    "org.scalikejdbc" %% "scalikejdbc-play-fixture" % ScalikejdbcPlayVersion,
    "org.skinny-framework" %% "skinny-orm" % "2.5.2",
    "org.flywaydb" %% "flyway-play" % "4.0.0",

    "org.scalawiki" %% "scalawiki-core" % ScalawikiVersion,
    "org.scalawiki" %% "scalawiki-wlx" % ScalawikiVersion,

    "com.typesafe.akka" %% "akka-stream" % "2.5.11",
    "com.typesafe.akka" %% "akka-http" % "10.0.13",

    "nl.grons" %% "metrics-scala" % "3.5.9",
    "com.typesafe.play" %% "play-mailer" % PlayMailerVersion,
    "com.typesafe.play" %% "play-mailer-guice" % PlayMailerVersion,
    "com.github.tototoshi" %% "scala-csv" % "1.3.4",
    "uk.org.lidalia" % "sysout-over-slf4j" % "1.0.2",
    guice,
    filters,

    specs2 % Test,
    jdbc % Test,
    "com.wix" % "wix-embedded-mysql" % "4.1.2" % Test,
    "com.h2database" % "h2" % "1.4.193" % Test)

  val dependencyOverrides = Seq(
    "commons-io" % "commons-io" % "2.5"
  )

  val dependencySettings = Seq(
    Keys.resolvers ++= resolvers,
    Keys.libraryDependencies ++= dependencies,
    Keys.dependencyOverrides ++= dependencyOverrides
  )

}