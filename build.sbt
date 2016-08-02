import sbt.Keys._

lazy val root = identity((project in file("."))
  .enablePlugins(PlayScala, DebianPlugin, RpmPlugin))

name := "wlxjury"

organization := "org.intracer"

version := "0.8"

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

scalaVersion := "2.11.8"

val scalikejdbcVersion = "2.2.9"
val scalikejdbcPlayVersion = "2.4.3"
val scalawikiVersion = "0.4.3"

resolvers += Resolver.bintrayRepo("intracer", "maven")

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  "org.webjars" %% "webjars-play" % "2.4.0",
  "com.adrianhurt" %% "play-bootstrap" % "1.0-P24-B3",
  "mysql" % "mysql-connector-java" % "5.1.35",
  "org.scalikejdbc" %% "scalikejdbc" % scalikejdbcVersion,
  "org.scalikejdbc" %% "scalikejdbc-config" % scalikejdbcVersion,
  "org.scalikejdbc" %% "scalikejdbc-play-initializer" % scalikejdbcPlayVersion,
  "org.scalikejdbc" %% "scalikejdbc-play-dbapi-adapter" % scalikejdbcPlayVersion,
  "org.scalikejdbc" %% "scalikejdbc-play-fixture" % scalikejdbcPlayVersion,
  "org.scalawiki" %% "scalawiki-core" % scalawikiVersion,
  "org.scalawiki" %% "scalawiki-wlx" % scalawikiVersion,
  "nl.grons" %% "metrics-scala" % "3.3.0_a2.3",
  "org.atmosphere" % "atmosphere-play" % "2.2.0",
  "com.typesafe.play" %% "play-mailer" % "4.0.0",
  "org.flywaydb" %% "flyway-play" % "2.3.0",
  jdbc, cache, filters,
  specs2 % Test,
  "org.scalamock" %% "scalamock-scalatest-support" % "3.2" % "test"
)

doc in Compile <<= target.map(_ / "none")

addCommandAlias(
  "packageAll", "; clean" +
    "; packageDebianSystemV" +
    "; clean " +
    "; packageDebianUpstart" +
    "; clean " +
    "; packageDebianSystemD" +
    "; clean " +
    "; packageRpmSystemV" +
    "; clean " +
    "; packageRpmUpstart" +
    "; clean " +
    "; packageRpmSystemD"
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
  "packageDebianSystemD", "; set serverLoading in Debian := com.typesafe.sbt.packager.archetypes.ServerLoader.Systemd" +
    "; internalPackageDebianSystemD"
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
  "packageRpmSystemD", "; set serverLoading in Rpm := com.typesafe.sbt.packager.archetypes.ServerLoader.Systemd" +
    "; internalPackageRpmSystemD"
)

lazy val internalPackageDebianSystemV = taskKey[File]("creates debian package with systemv")
lazy val internalPackageDebianUpstart = taskKey[File]("creates debian package with upstart")
lazy val internalPackageDebianSystemD = taskKey[File]("creates debian package with systemd")

lazy val internalPackageRpmSystemV = taskKey[File]("creates rpm package with systemv")
lazy val internalPackageRpmUpstart = taskKey[File]("creates rpm package with upstart")
lazy val internalPackageRpmSystemD = taskKey[File]("creates rpm package with systemd")

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

internalPackageDebianSystemD := {
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

internalPackageRpmSystemD := {
  val output = baseDirectory.value / "package" / s"wlxjury-systemd-${version.value}.rpm"
  val rpmFile = (packageBin in Rpm).value
  IO.move(rpmFile, output)
  output
}