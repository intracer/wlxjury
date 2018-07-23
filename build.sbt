import sbt.Keys._

lazy val root = identity((project in file("."))
  .enablePlugins(PlayScala, DebianPlugin, RpmPlugin, JavaAppPackaging))

name := "wlxjury"

organization := "org.intracer"

version := "0.9"

//rpmRelease := "1"

rpmVendor := "intracer"

rpmUrl := Some("https://github.com/intracer/wlxjury")

rpmLicense := Some("ASL 2.0")

packageSummary := "WLX Jury Tool is an image selection and rating tool for Wiki Loves Monuments and Wiki Loves Earth contests"

packageDescription :=
  """On gallery page jurors or organizing committee can browse the images, see the rating or selection status of each image, open large image view page.
    |Images can be filtered by their status - unrated, and selected or rejected in selection rounds or rated in rating rounds.
    |In rating rounds images are sorted by their rating.
    |Organizing committee can see the votes of each juror separately or the overall rating of all jurors together.
    |In large image view there is vertical ribbon of image thumbnails on the left and large currently viewed image on the right.
    |User can go backward or forward with navigation buttons or arrow keys on the keyboard, or can click the thumbnails in the ribbon
    |Juror can select, reject or rate the currently viewed image.
    |Juror can also comment the image and see other jurors comments.
    |Organizing committee can see the ratings and comments given by all jurors to the image.
    |From large image view one can return to gallery view, visit image page on commons by clicking the large image, or open full resolution version of the image. Caption of the full resolution image version link shows image resolution.
  """.stripMargin.replace('\n', ' ')

maintainer := "Ilya Korniiko <intracer@gmail.com>"

debianPackageDependencies in Debian ++= Seq("java8-runtime")

debianPackageRecommends in Debian ++= Seq("virtual-mysql-server")

scalaVersion := "2.12.6"

val ScalikejdbcVersion = "3.2.2"
val ScalikejdbcPlayVersion = "2.6.0-scalikejdbc-3.2"
val ScalawikiVersion = "0.5.0"
val PlayMailerVersion = "6.0.1"

resolvers += Resolver.bintrayRepo("intracer", "maven")

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
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
  guice, filters,
  specs2 % Test,
  jdbc % Test,
  "com.wix" % "wix-embedded-mysql" % "4.1.2" % Test,
  "com.h2database" % "h2" % "1.4.193" % Test)

dependencyOverrides ++= Seq(
  "commons-io" % "commons-io" % "2.5"
)

routesGenerator := StaticRoutesGenerator

//doc in Compile <<= target.map(_ / "none")

javaOptions in Test += "-Dconfig.file=test/resources/application.conf"

addCommandAlias(
  "packageAll", "; clean" +
    "; packageDebianSystemV" +
    "; clean " +
    "; packageDebianUpstart" +
    "; clean " +
    "; packageDebianSystemd" +
    "; clean " +
    "; packageRpmSystemV" +
    "; clean " +
    "; packageRpmUpstart" +
    "; clean " +
    "; packageRpmSystemd"
)

addCommandAlias(
  "packageDebianSystemV", "; set serverLoading in Debian := com.typesafe.sbt.packager.archetypes.ServerLoader.SystemV" +
    "; internalPackageDebianSystemV"
)

addCommandAlias(
  "packageDebianUpstart", "; set serverLoading in Debian := com.typesafe.sbt.packager.archetypes.ServerLoader.Upstart" +
    "; internalPackageDebianUpstart"
)

addCommandAlias(
  "packageDebianSystemd", "; set serverLoading in Debian := com.typesafe.sbt.packager.archetypes.ServerLoader.Systemd" +
    "; internalPackageDebianSystemd"
)

addCommandAlias(
  "packageRpmSystemV", "; set serverLoading in Rpm := com.typesafe.sbt.packager.archetypes.ServerLoader.SystemV" +
    "; internalPackageRpmSystemV"
)

addCommandAlias(
  "packageRpmUpstart", "; set serverLoading in Rpm := com.typesafe.sbt.packager.archetypes.ServerLoader.Upstart" +
    "; internalPackageRpmUpstart"
)

addCommandAlias(
  "packageRpmSystemd", "; set serverLoading in Rpm := com.typesafe.sbt.packager.archetypes.ServerLoader.Systemd" +
    "; internalPackageRpmSystemd"
)

lazy val internalPackageDebianSystemV = taskKey[File]("creates debian package with systemv")
lazy val internalPackageDebianUpstart = taskKey[File]("creates debian package with upstart")
lazy val internalPackageDebianSystemd = taskKey[File]("creates debian package with systemd")

lazy val internalPackageRpmSystemV = taskKey[File]("creates rpm package with systemv")
lazy val internalPackageRpmUpstart = taskKey[File]("creates rpm package with upstart")
lazy val internalPackageRpmSystemd = taskKey[File]("creates rpm package with systemd")

internalPackageDebianSystemV := {
  val output = baseDirectory.value / "package" / s"wlxjury-systemv-${version.value}.deb"
  val debianFile = (packageBin in Debian).value
  IO.move(debianFile, output)
  output
}

internalPackageDebianUpstart := {
  val output = baseDirectory.value / "package" / s"wlxjury-upstart-${version.value}.deb"
  val debianFile = (packageBin in Debian).value
  IO.move(debianFile, output)
  output
}

internalPackageDebianSystemd := {
  val output = baseDirectory.value / "package" / s"wlxjury-systemd-${version.value}.deb"
  val debianFile = (packageBin in Debian).value
  IO.move(debianFile, output)
  output
}

internalPackageRpmSystemV := {
  val output = baseDirectory.value / "package" / s"wlxjury-systemv-${version.value}.rpm"
  val rpmFile = (packageBin in Rpm).value
  IO.move(rpmFile, output)
  output
}

internalPackageRpmUpstart := {
  val output = baseDirectory.value / "package" / s"wlxjury-upstart-${version.value}.rpm"
  val rpmFile = (packageBin in Rpm).value
  IO.move(rpmFile, output)
  output
}

internalPackageRpmSystemd := {
  val output = baseDirectory.value / "package" / s"wlxjury-systemd-${version.value}.rpm"
  val rpmFile = (packageBin in Rpm).value
  IO.move(rpmFile, output)
  output
}