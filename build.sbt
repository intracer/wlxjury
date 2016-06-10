name := "wlxjury"

version := "1.0-SNAPSHOT"

//rpmRelease := "1"
//
//rpmVendor := "typesafe"
//
//rpmUrl := Some("http://github.com/paulp/sbt-extras")
//
//rpmLicense := Some("GPL")
//
//packageSummary := "WLX Jury Tool is an image selection and rating tool for Wiki Loves Monuments and Wiki Loves Earth contests"
//
//packageDescription :=
//  """On gallery page jurors or organizing committee can browse the images, see the rating or selection status of each image, open large image view page.
//    |
//    |Images can be filtered by their status - unrated, and selected or rejected in selection rounds or rated in rating rounds.
//    |
//    |In rating rounds images are sorted by their rating.
//    |
//    |Organizing committee can see the votes of each juror separately or the overall rating of all jurors together.
//    |
//    |In large image view there is vertical ribbon of image thumbnails on the left and large currently viewed image on the right.
//    |
//    |User can go backward or forward with navigation buttons or arrow keys on the keyboard, or can click the thumbnails in the ribbon
//    |
//    |Juror can select, reject or rate the currently viewed image.
//    |
//    |Juror can also comment the image and see other jurors comments.
//    |
//    |Organizing committee can see the ratings and comments given by all jurors to the image.
//    |
//    |From large image view one can return to gallery view, visit image page on commons by clicking the large image, or open full resolution version of the image. Caption of the full resolution image version link shows image resolution.
//  """.stripMargin
//
//maintainer := "Ilya Korniiko <intracer@gmail.com>"

scalaVersion := "2.11.8"

val scalikejdbcVersion = "2.2.9"
val scalikejdbcPlayVersion = "2.4.3"

//resolvers += Resolver.url("Intracer bintray", url("http://dl.bintray.com/intracer/maven"))

resolvers += Resolver.bintrayRepo("intracer", "maven")

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

//routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  "org.webjars" %% "webjars-play" % "2.4.0",
  "com.adrianhurt" %% "play-bootstrap" % "1.0-P24-B3",
"mysql" % "mysql-connector-java" % "5.1.35",
  "org.scalikejdbc" %% "scalikejdbc" % scalikejdbcVersion,
  "org.scalikejdbc" %% "scalikejdbc-config" % scalikejdbcVersion,
  "org.scalikejdbc" %% "scalikejdbc-play-initializer" % scalikejdbcPlayVersion,
  "org.scalikejdbc" %% "scalikejdbc-play-dbapi-adapter" % scalikejdbcPlayVersion,
  "org.scalikejdbc" %% "scalikejdbc-play-fixture" % scalikejdbcPlayVersion,
  "org.slf4j" % "slf4j-simple" % "[1.7,)",
  "org.apache.commons" % "commons-email" % "1.3.2",
  "org.scalawiki" %% "scalawiki-core" % "0.4.2",
  "org.scalawiki" %% "scalawiki-wlx" % "0.4.2",
  "nl.grons" %% "metrics-scala" % "3.3.0_a2.3",
  "org.atmosphere" % "atmosphere-play" % "2.2.0",
  "com.typesafe.play" %% "play-mailer" % "4.0.0",
  jdbc, cache, filters, evolutions,
  specs2 % Test,
  "org.scalamock" %% "scalamock-scalatest-support" % "3.2" % "test"
)

lazy val root = (project in file (".") ).enablePlugins (PlayScala)
//lazy val root = (project in file(".")).enablePlugins(PlayScala, DeploySSH).settings(
//  deployHomeConfigFiles ++= Seq("wm/wmua.conf"),
//  //  deployConfigs ++= Seq(
//  //    ServerConfig("wmua", "wlm.org.ua")
//  //  ),
//  deployArtifacts ++= Seq(
//    //`jar` file from `packageBin in Compile` task will be deployed to `/tmp/` directory
//    //    //directory `stage` generated by `sbt-native-packager` will be deployed to `~/stage_1.1_release/` directory
//    ArtifactSSH(
//      dist.value,
//      s"jury_deploy")
////    ,ArtifactSSH(
////      debianNativePackaging.value,
////      s"jury_deploy")
//  )
//)