import sbt.Keys._

lazy val root = identity((project in file("."))
  .enablePlugins(PlayScala, PlayNettyServer, DebianPlugin, SystemdPlugin, JavaServerAppPackaging))
  .disablePlugins(PlayAkkaHttpServer)

name := "wlxjury"

organization := "org.intracer"

version := "0.13"

scalaVersion := "2.12.10"

val ScalikejdbcVersion = "3.3.5"
val ScalikejdbcPlayVersion = "2.6.0-scalikejdbc-3.3"
val ScalawikiVersion = "0.6.6.1"
val PlayMailerVersion = "6.0.1"
val MockServerVersion = "5.7.0"

resolvers += Resolver.bintrayRepo("intracer", "maven")

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

resolvers += Resolver.jcenterRepo

resolvers += Resolver.mavenLocal

libraryDependencies ++= Seq(
  "org.webjars" %% "webjars-play" % "2.6.3",
  "com.adrianhurt" %% "play-bootstrap" % "1.5.1-P26-B3",
  "org.webjars" % "bootstrap" % "3.3.7-1" exclude("org.webjars", "jquery"),
  "org.webjars" % "jquery" % "3.2.1",

  "mysql" % "mysql-connector-java" % "8.0.33",
  "org.scalikejdbc" %% "scalikejdbc" % ScalikejdbcVersion,
  "org.scalikejdbc" %% "scalikejdbc-config" % ScalikejdbcVersion,
  "org.scalikejdbc" %% "scalikejdbc-play-initializer" % ScalikejdbcPlayVersion,
  "org.scalikejdbc" %% "scalikejdbc-play-dbapi-adapter" % ScalikejdbcPlayVersion,
  "org.scalikejdbc" %% "scalikejdbc-play-fixture" % ScalikejdbcPlayVersion,
  "org.skinny-framework" %% "skinny-orm" % "2.5.2",
  "org.flywaydb" %% "flyway-play" % "4.0.0",

  "org.scalawiki" %% "scalawiki-core" % ScalawikiVersion,
  "org.scalawiki" %% "scalawiki-wlx" % ScalawikiVersion,

  "com.typesafe.akka" %% "akka-stream" % "2.5.26",
  "com.typesafe.akka" %% "akka-http" % "10.1.10",

  "nl.grons" %% "metrics-scala" % "4.0.0",
  "com.typesafe.play" %% "play-mailer" % PlayMailerVersion,
  "com.typesafe.play" %% "play-mailer-guice" % PlayMailerVersion,
  "com.github.tototoshi" %% "scala-csv" % "1.3.6",
  "uk.org.lidalia" % "sysout-over-slf4j" % "1.0.2",
  "javax.xml.bind" % "jaxb-api" % "2.3.1",
  guice, filters,
  specs2 % Test,
  jdbc % Test,
  "com.wix" % "wix-embedded-mysql" % "4.6.1" % Test,
  "org.mock-server" % "mockserver-netty" % MockServerVersion % Test,
  "net.java.dev.jna" % "jna" % "4.5.0" % Test,
  "net.java.dev.jna" % "jna-platform" % "4.5.0" % Test,
  "com.h2database" % "h2" % "1.4.193" % Test
)

dependencyOverrides ++= Seq(
  "commons-io" % "commons-io" % "2.5"
)

routesGenerator := StaticRoutesGenerator

//doc in Compile <<= target.map(_ / "none")

javaOptions in Test += "-Dconfig.file=test/resources/application.conf"
javaOptions in Test += "-Djna.nosys=true"

//rpmRelease := "1"

rpmRequirements ++= Seq("java-1.8.0-openjdk", "bash")

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

debianPackageRecommends in Debian ++= Seq("virtual-mysql-server")

addCommandAlias(
  "packageAll", "; clean" +
    "; packageDebSystemV" +
    "; clean " +
    "; packageDebUpstart" +
    "; clean " +
    "; packageDebSystemd" +
    "; clean " +
    "; packageRpmSystemV" +
    "; clean " +
    "; packageRpmUpstart" +
    "; clean " +
    "; packageRpmSystemd"
)

addCommandAlias(
  "packageDebSystemV", "; set serverLoading in Debian := Some(com.typesafe.sbt.packager.archetypes.systemloader.ServerLoader.SystemV)" +
    "; internalPackageDebianSystemV"
)

addCommandAlias(
  "packageDebUpstart", "; set serverLoading in Debian := Some(com.typesafe.sbt.packager.archetypes.systemloader.ServerLoader.Upstart)" +
    "; internalPackageDebianUpstart"
)

addCommandAlias(
  "packageDebSystemd", "; set serverLoading in Debian := Some(com.typesafe.sbt.packager.archetypes.systemloader.ServerLoader.Systemd)" +
    "; internalPackageDebianSystemd"
)

addCommandAlias(
  "packageRpmSystemV", "; set serverLoading in Rpm := Some(com.typesafe.sbt.packager.archetypes.systemloader.ServerLoader.SystemV)" +
    "; internalPackageRpmSystemV"
)

addCommandAlias(
  "packageRpmUpstart", "; set serverLoading in Rpm := Some(com.typesafe.sbt.packager.archetypes.systemloader.ServerLoader.Upstart)" +
    "; internalPackageRpmUpstart"
)

addCommandAlias(
  "packageRpmSystemd", "; set serverLoading in Rpm := Some(com.typesafe.sbt.packager.archetypes.systemloader.ServerLoader.Systemd)" +
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