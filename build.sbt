import play.sbt.routes.RoutesKeys.routesGenerator
import sbt.Keys._

name := "wlxjury"

organization := "org.intracer"

version := "0.11-SNAPSHOT"

scalaVersion := "2.12.6"

lazy val root = identity((project in file("."))
  .enablePlugins(PlayScala, DebianPlugin, RpmPlugin, JavaAppPackaging))
  .settings(
    Dependencies.dependencySettings,
    PackageSettings.packageSettings,
    routesGenerator := StaticRoutesGenerator,
    javaOptions in Test += "-Dconfig.file=test/resources/application.conf"
  )

