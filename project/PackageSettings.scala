import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.debian.DebianPlugin.autoImport._
import com.typesafe.sbt.packager.rpm.RpmPlugin.autoImport._
import sbt.Keys._
import sbt._

object PackageSettings extends AutoPlugin {

  val packageSettings = Seq(

    rpmRequirements ++= Seq("java-1.8.0-openjdk", "bash"),

    rpmVendor := "intracer",

    rpmUrl := Some("https://github.com/intracer/wlxjury"),

    rpmLicense := Some("ASL 2.0"),

    packageSummary := "WLX Jury Tool is an image selection and rating tool for Wiki Loves Monuments and Wiki Loves Earth contests",

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
      """.stripMargin.replace('\n', ' '),

    maintainer := "Ilya Korniiko <intracer@gmail.com>",

    debianPackageDependencies in Debian ++= Seq("java8-runtime"),

    debianPackageRecommends in Debian ++= Seq("virtual-mysql-server")
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

}
