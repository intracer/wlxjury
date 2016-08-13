package controllers

import _root_.db.scalikejdbc.ContestJuryJdbc
import com.codahale.metrics.{JmxReporter, MetricRegistry}
import org.intracer.wmua._
import org.scalawiki.MwBot
import play.Play
import play.api._
import scalikejdbc.{GlobalSettings, LoggingSQLAndTimeSettings}

object Global {
  final val COMMONS_WIKIMEDIA_ORG = "commons.wikimedia.org"

  val gallerySizeX = 300
  val gallerySizeY = 200
  val gallerySizeXDouble = 600
  val gallerySizeYDouble = 400
  val thumbSizeX = 150
  val thumbSizeY = 120
  val largeSizeX = 1280
  val largeSizeY = 1180

  private val galleryUrls = collection.mutable.Map[Long, String]()
  private val largeUrls = collection.mutable.Map[Long, String]()
  private val thumbUrls = collection.mutable.Map[Long, String]()

  val metrics = new MetricRegistry()

  val projectRoot = Play.application().path()

  lazy val commons = MwBot.fromHost(COMMONS_WIKIMEDIA_ORG)

  def onStart(app: Application) {
    Logger.info("Application has started")

    val reporter = JmxReporter.forRegistry(metrics).build()
    reporter.start()

    //KOATUU.load()
  }

  def initCountry(category: String, countryOpt: Option[String]) = {
    val country = countryOpt.fold(category.replace("Category:Images from Wiki Loves Earth 2014 in ", ""))(identity)

    //"Ukraine"
    val contestOpt = ContestJuryJdbc.byCountry.get(country).flatMap(_.headOption)

    for (contest <- contestOpt) {
      globalRefactor.initContest(category, contest)
    }
  }

  def globalRefactor = {
    val commons = MwBot.fromHost(MwBot.commons)
    new GlobalRefactor(commons)
  }

  def initContestFiles(filesInCategory: Seq[Image]) {

    for (file <- filesInCategory) {
      val galleryUrl = resizeTo(file, gallerySizeX, gallerySizeY)
      val thumbUrl = resizeTo(file, thumbSizeX, thumbSizeY)
      val largeUrl = resizeTo(file, largeSizeX, largeSizeY)

      galleryUrls(file.pageId) = galleryUrl
      thumbUrls(file.pageId) = thumbUrl
      largeUrls(file.pageId) = largeUrl
    }
  }

  def resizeTo(info: Image, resizeToWidth: Int, resizeToHeight: Int): String = {
    val h = info.height
    val w = info.width

    val px = info.resizeTo(resizeToWidth, resizeToHeight)

    val isPdf = info.title.toLowerCase.endsWith(".pdf")
    val isTif = info.title.toLowerCase.endsWith(".tif")

    val url = info.url
    if (px < w || isPdf || isTif) {
      val lastSlash = url.lastIndexOf("/")
      val utf8Size = info.title.getBytes("UTF8").length
      val thumbStr = if (utf8Size > 165) {
        "thumbnail.jpg"
      } else {
        url.substring(lastSlash + 1)
      }
      url.replace("//upload.wikimedia.org/wikipedia/commons/", "//upload.wikimedia.org/wikipedia/commons/thumb/") + "/" +
        (if (isPdf) "page1-" else
        if (isTif) "lossy-page1-" else
          "") +
        px + "px-" + thumbStr + (if (isPdf || isTif) ".jpg" else "")
    } else {
      url
    }
  }

}