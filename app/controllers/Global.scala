package controllers

import java.io.{File, FileReader}
import java.util.Properties

import client.{HttpClientImpl, MwBot}
import org.intracer.wmua._
import play.Play
import play.api._
import play.api.libs.concurrent.Akka
import scalikejdbc.{GlobalSettings, LoggingSQLAndTimeSettings}

import scala.collection.JavaConverters._


object Global {
  final val COMMONS_WIKIMEDIA_ORG = "commons.wikimedia.org"

  val gallerySize = 300
  val thumbSize = 150
  val largeSize = 1280

  var galleryUrls = collection.mutable.Map[Long, String]()
  var largeUrls = collection.mutable.Map[Long, String]()
  var thumbUrls = collection.mutable.Map[Long, String]()

  val projectRoot = Play.application().path()

  //  initUrls()

  import play.api.Play.current

  val http = new HttpClientImpl(Akka.system)

  val commons = new MwBot(http, Akka.system, COMMONS_WIKIMEDIA_ORG)

//  var contestByCountry: Map[String, Seq[ContestJury]] = Map.empty


  def onStart(app: Application) {
    Logger.info("Application has started")

    GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
      enabled = true,
      singleLineMode = false,
      printUnprocessedStackTrace = false,
      stackTraceDepth = 15,
      logLevel = 'debug,
      warningEnabled = false,
      warningThresholdMillis = 3000L,
      warningLogLevel = 'warn
    )


//    contestByCountry =

    KOATUU.load()
    contestImages()

  }


  def contestImages() {
    //Await.result(commons.login("***REMOVED***", "***REMOVED***"), 1.minute)

    Global.initContestFiles(ImageJdbc.findAll())

//    commons.categoryMembers(PageQuery.byTitle("Category:Images from Wiki Loves Earth 2014"), Set(Namespace.CATEGORY)) flatMap {
//      categories =>
//        val filtered = categories.filter(c => c.title.startsWith("Category:Images from Wiki Loves Earth 2014 in "))
//        Future.traverse(filtered) {
//          category =>
//
//            if (category.title.contains("Ukraine"))
//              initUkraine("Category:WLE 2014 in Ukraine Round One", Some("Ukraine"))
//            else
//              initUkraine(category.title)
//
//            //    initLists()
//
//            Future(1)
//        }
//    }
  }

  def initCountry(category: String, countryOpt: Option[String]) = {
    val country = countryOpt.fold(category.replace("Category:Images from Wiki Loves Earth 2014 in ", ""))(identity)

    //"Ukraine"
    val contestOpt = ContestJury.byCountry.get(country).flatMap(_.headOption)

    for (contest <- contestOpt) {
      GlobalRefactor.initContest(category, contest)
    }
  }


  def initContestFiles(filesInCategory: Seq[Image]) {

    for (file <- filesInCategory) {
      val galleryUrl = resizeTo(file, gallerySize)
      val thumbUrl = resizeTo(file, thumbSize)
      val largeUrl = resizeTo(file, largeSize)

      galleryUrls(file.pageId) = galleryUrl
      thumbUrls(file.pageId) = thumbUrl
      largeUrls(file.pageId) = largeUrl
    }
  }

  def resizeTo(info: Image, resizeTo: Int) = {
    val h = info.height
    val w = info.width

    val px = if (w >= h) Math.min(w, resizeTo)
    else {
      if (h > resizeTo) {
        val ratio = h.toFloat / resizeTo
        w / ratio
      } else {
        w
      }
    }

    val isPdf = info.title.toLowerCase.endsWith(".pdf")

    val url = info.url
    if (px < w || isPdf) {
      val lastSlash = url.lastIndexOf("/")
      url.replace("//upload.wikimedia.org/wikipedia/commons/", "//upload.wikimedia.org/wikipedia/commons/thumb/") + "/" + (if (isPdf) "page1-" else "") +
        px.toInt + "px-" + url.substring(lastSlash + 1) + (if (isPdf) ".jpg" else "")
    } else {
      url
    }
  }



  def loadFileCache(file: File): Map[String, String] = {
    val galleryUrlsProps = new Properties
    galleryUrlsProps.load(new FileReader(file))

    Logger.info("loadFileCache file " + file)
    Logger.info("loadFileCache size " + galleryUrlsProps.size())
    Logger.info("loadFileCache head " + galleryUrlsProps.asScala.head)

    galleryUrlsProps.asScala.toMap
  }

}