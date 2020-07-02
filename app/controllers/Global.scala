package controllers

import java.net.URLEncoder

import _root_.db.scalikejdbc.ContestJury
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.ConfigFactory
import org.intracer.wmua._
import org.scalawiki.MwBot

object Global {
  final val COMMONS_WIKIMEDIA_ORG = "commons.wikimedia.org"

  val gallerySizeX = 300
  val gallerySizeY = 250
  val thumbSizeX = 150
  val thumbSizeY = 120
  val largeSizeX = 1280
  val largeSizeY = 1100

  val smallSizes  = Seq(
    (gallerySizeX, gallerySizeY),
    (thumbSizeX, thumbSizeY)
  )

  val sizeFactors = Seq(1.0, 1.5, 2.0)

  val metrics = new MetricRegistry()

  lazy val commons = MwBot.fromHost(COMMONS_WIKIMEDIA_ORG)

  lazy val thumbsHost = ConfigFactory.load().getString("wlxjury.thumbs.host")

  val useLegacyThumbUrl = true
  val thumbUrl: (Image, Int, Int) => String = if (useLegacyThumbUrl) legacyThumbUlr else thumbPhpUrl

  def initCountry(category: String, countryOpt: Option[String]) = {
    val country = countryOpt.fold(category.replace("Category:Images from Wiki Loves Earth 2014 in ", ""))(identity)

    //"Ukraine"
    val contestOpt = ContestJury.where('country -> country).apply().headOption

    for (contest <- contestOpt) {
      globalRefactor.initContest(category, contest)
    }
  }

  def globalRefactor = {
    val commons = MwBot.fromHost(MwBot.commons)
    new GlobalRefactor(commons)
  }

  def resizeTo(info: Image, resizeToWidth: Int, resizeToHeight: Int): String = {

    val px = info.resizeTo(resizeToWidth, resizeToHeight)

    val isPdf = info.title.toLowerCase.endsWith(".pdf")
    val isTif = info.title.toLowerCase.endsWith(".tif")
    val isSvg = info.title.toLowerCase.endsWith(".svg")

    if (px < info.width || isPdf || isTif || isSvg) {
      thumbUrl(info, px, resizeToHeight)
    } else {
      info.url.getOrElse("")
    }
  }

  def resizeTo(info: Image, resizeToHeight: Int): String = {

    val px = info.resizeTo(resizeToHeight)

    val isPdf = info.title.toLowerCase.endsWith(".pdf")
    val isTif = info.title.toLowerCase.endsWith(".tif")
    val isSvg = info.title.toLowerCase.endsWith(".svg")

    if (px < info.width || isPdf || isTif || isSvg) {
      thumbUrl(info, px, resizeToHeight)
    } else {
      info.url.getOrElse("")
    }
  }

  def srcSet(image: Image, resizeToWidth: Int, resizeToHeight: Int) = {
    s"${resizeTo(image, (resizeToWidth * 1.5).toInt, (resizeToHeight * 1.5).toInt)} 1.5x, ${resizeTo(image, resizeToWidth * 2, resizeToHeight * 2)} 2x"
  }

  def thumbPhpUrl(info: Image, px: Int, resizeToHeight: Int) = {
    val file = URLEncoder.encode(info.title.replaceFirst("File:", "").replace(" ", "_"), "UTF-8")
    s"https://commons.wikimedia.org/w/thumb.php?f=$file&w=$px"
  }

  def legacyThumbUlr(info: Image, px: Int, resizeToHeight: Int) = {
    val isPdf = info.title.toLowerCase.endsWith(".pdf")
    val isTif = info.title.toLowerCase.endsWith(".tif")

    val url = info.url.getOrElse("")
    val lastSlash = url.lastIndexOf("/")
    val utf8Size = info.title.getBytes("UTF8").length
    val thumbStr = if (utf8Size > 165) {
      "thumbnail.jpg"
    } else {
      url.substring(lastSlash + 1)
    }

    val imageHost = if (resizeToHeight <= gallerySizeY * 2) thumbsHost else "upload.wikimedia.org"

    url.replace("//upload.wikimedia.org/wikipedia/commons/", s"//$imageHost/wikipedia/commons/thumb/") + "/" +
      (if (isPdf) "page1-" else
      if (isTif) "lossy-page1-" else
        "") +
      px + "px-" + thumbStr + (if (isPdf || isTif) ".jpg" else "")
  }
}