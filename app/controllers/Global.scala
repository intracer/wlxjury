package controllers

import play.api._
import java.io.{FileReader, File}
import play.Play
import java.util.Properties
import scala.collection.JavaConverters._
import scala.concurrent.Future
import client.{HttpClientImpl, MwBot}
import play.api.libs.concurrent.Akka
import client.dto._
import org.intracer.wmua.{Image, Contest}


object Global {
  final val COMMONS_WIKIMEDIA_ORG = "commons.wikimedia.org"

  //  lazy val w: Wiki = login(COMMONS_WIKIMEDIA_ORG, "***REMOVED***", "***REMOVED***1")

  val dir = "public/wlm"

  var galleryUrls = collection.mutable.Map[String, String]()
  var largeUrls = collection.mutable.Map[String, String]()
  var thumbUrls = collection.mutable.Map[String, String]()

  val projectRoot = Play.application().path()


  //  initUrls()

  import play.api.Play.current
  import play.api.libs.concurrent.Execution.Implicits._

  val http = new HttpClientImpl(Akka.system)


  val commons = new MwBot(http, Akka.system, COMMONS_WIKIMEDIA_ORG)

  var contestByCountry: Map[String, Seq[Contest]] = Map.empty


  def onStart(app: Application) {
    Logger.info("Application has started")

    contestByCountry = Contest.byCountry

    commons.login("***REMOVED***", "***REMOVED***1")

    KOATUU.load()
    contestImages()

  }

  def contestImages() {
    //    commons.categoryMembers(PageQuery.byTitle("Category:Images from Wiki Loves Earth 2014"), Set(Namespace.CATEGORY_NAMESPACE)) flatMap {
    //      categories =>
    //        val filtered = categories.filter(c => c.title.startsWith("Category:Images from Wiki Loves Earth 2014 in Ukraine")) // TODO all countries
    //        Future.traverse(filtered) {
    //          category =>

    initUkraine("Category:Images from Wiki Loves Earth 2014 in Ukraine")

    //            Future(1)
    //        }
    //    }
  }

  def initUkraine(category: String) {
    val country = category.replace("Category:Images from Wiki Loves Earth 2014 in ", "")
    val contestOpt = contestByCountry.get(country).flatMap(_.headOption)

    for (contest <- contestOpt) {
      val images = Image.findByContest(contest.id)

      if (images.isEmpty) {
        val query: SinglePageQuery = PageQuery.byTitle(category)
        //PageQuery.byId(category.pageid)
        initImagesFromCategory(contest, query)
      } else {
        initContestFiles(contest, images)
      }
    }
  }

  def initImagesFromCategory(contest: Contest, query: SinglePageQuery): Future[Unit] = {
    commons.imageInfoByGenerator("categorymembers", "cm", query, Set(Namespace.FILE_NAMESPACE)).map {
      filesInCategory =>
        val newImages: Seq[Image] = filesInCategory.flatMap(page => Image.fromPage(page, contest)).sortBy(_.pageId)

        commons.revisionsByGenerator("categorymembers", "cm", query,
          Set.empty, Set("content", "timestamp", "user", "comment")) map {
          pages =>

            val idRegex = """(\d\d)-(\d\d\d)-(\d\d\d\d)"""
            val ids: Seq[Option[String]] = pages.sortBy(_.pageid)
              .flatMap(_.text.map(commons.getTemplateParam(_, "UkrainianNaturalHeritageSite")))
              .map(id => if (id.matches(idRegex)) Some(id) else None)

            val imagesWithIds = newImages.zip(ids).map {
              case (image, Some(id)) => image.copy(monumentId = Some(id))
              case (image, None) => image
            }

            Image.batchInsert(imagesWithIds)
            initContestFiles(contest, imagesWithIds)
        }

    }
  }

  def initContestFiles(contest: Contest, filesInCategory: Seq[Image]) {

    val gallerySize = 300
    val thumbSize = 150
    val largeSize = 1280
    for (file <- filesInCategory) {
      val galleryUrl = resizeTo(file, gallerySize)
      val thumbUrl = resizeTo(file, thumbSize)
      val largeUrl = resizeTo(file, largeSize)

      galleryUrls(file.title) = galleryUrl
      thumbUrls(file.title) = thumbUrl
      largeUrls(file.title) = largeUrl
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


  def initUrls() {

    //    val galleryUrlsFiles = (1 to 10).map(i => new File(s"${projectRoot.getAbsolutePath}/conf/urls/galleryUrls${i}.txt"))
    //    val largeUrlsFiles = (1 to 10).map(i => new File(s"${projectRoot.getAbsolutePath}/conf/urls/largeUrls${i}.txt"))
    //    val thumbsUrlsFiles = (1 to 10).map(i => new File(s"${projectRoot.getAbsolutePath}/conf/urls/thumbUrls${i}.txt"))
    //
    //    Logger.info("galleryUrlsFiles" + galleryUrlsFiles)
    //    Logger.info("largeUrlsFiles" + largeUrlsFiles)
    //    Logger.info("thumbsUrlsFiles" + thumbsUrlsFiles)
    //
    //    galleryUrls = galleryUrlsFiles.map(loadFileCache).fold(Map[String, String]())(_ ++ _)
    //    largeUrls = largeUrlsFiles.map(loadFileCache).fold(Map[String, String]())(_ ++ _)
    //    thumbUrls = thumbsUrlsFiles.map(loadFileCache).fold(Map[String, String]())(_ ++ _)

    //files = SortedSet[String](galleryUrls.keySet.toSeq:_*).toSeq.slice(0, 1500)

    //    for (file <- files) {
    //      thumbUrls.put(file, w.getImageUrl(file, 150, 120))
    //      galleryUrls.put(file, w.getImageUrl(file, 300, 200))
    //      largeUrls.put(file, w.getImageUrl(file, 1280, 1024))
    //    }

    KOATUU.load()
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