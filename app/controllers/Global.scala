package controllers

import client.{HttpClientImpl, MwBot}
import com.codahale.metrics.{JmxReporter, MetricRegistry}
import org.intracer.wmua._
import play.Play
import play.api._
import play.api.libs.concurrent.Akka
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

    val reporter = JmxReporter.forRegistry(metrics).build()
    reporter.start()

//    contestByCountry =

    KOATUU.load()
    contestImages()

  }


  def contestImages() {
    //Await.result(commons.login("***REMOVED***", "***REMOVED***"), 1.minute)

    // Global.initContestFiles(ImageJdbc.findAll())

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
      url.replace("//upload.wikimedia.org/wikipedia/commons/", "//upload.wikimedia.org/wikipedia/commons/thumb/") + "/" +
        (if (isPdf) "page1-" else
        if (isTif) "lossy-page1-" else
          "") +
        px + "px-" + url.substring(lastSlash + 1) + (if (isPdf || isTif) ".jpg" else "")
    } else {
      url
    }
  }

}