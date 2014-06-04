package controllers


import play.api._
import org.wikipedia.Wiki
import java.io.{FileReader, File}
import play.Play
import java.util.Properties
import scala.collection.JavaConverters._
import scala.collection.SortedSet
import scala.concurrent.Await
import client.{HttpClientImpl, MwBot}
import play.api.libs.concurrent.Akka


object Global extends GlobalSettings {
  final val COMMONS_WIKIMEDIA_ORG = "commons.wikimedia.org"

//  lazy val w: Wiki = login(COMMONS_WIKIMEDIA_ORG, "***REMOVED***", "***REMOVED***1")

  val  dir  = "public/wlm"

  var galleryUrls = Map[String, String]()
  var largeUrls = Map[String, String]()
  var thumbUrls = Map[String, String]()

  val projectRoot = Play.application().path()

//  initUrls()

  import play.api.Play.current

  val http = new HttpClientImpl(Akka.system)


  val commons = new MwBot(http, Akka.system, COMMONS_WIKIMEDIA_ORG)


  override def onStart(app: Application) {
    Logger.info("Application has started")

    commons.login("***REMOVED***", "***REMOVED***1")
  }


  def initUrls() {

  val galleryUrlsFiles = (1 to 10).map(i=> new File(s"${projectRoot.getAbsolutePath}/conf/urls/galleryUrls${i}.txt"))
  val largeUrlsFiles = (1 to 10).map(i=> new File(s"${projectRoot.getAbsolutePath}/conf/urls/largeUrls${i}.txt"))
  val thumbsUrlsFiles = (1 to 10).map(i=> new File(s"${projectRoot.getAbsolutePath}/conf/urls/thumbUrls${i}.txt"))

    Logger.info("galleryUrlsFiles" + galleryUrlsFiles)
    Logger.info("largeUrlsFiles" + largeUrlsFiles)
    Logger.info("thumbsUrlsFiles" + thumbsUrlsFiles)

    galleryUrls = galleryUrlsFiles.map(loadFileCache).fold(Map[String, String]())( _ ++ _)
    largeUrls = largeUrlsFiles.map(loadFileCache).fold(Map[String, String]())( _ ++ _)
    thumbUrls = thumbsUrlsFiles.map(loadFileCache).fold(Map[String, String]())( _ ++ _)

    //files = SortedSet[String](galleryUrls.keySet.toSeq:_*).toSeq.slice(0, 1500)

//    for (file <- files) {
//      thumbUrls.put(file, w.getImageUrl(file, 150, 120))
//      galleryUrls.put(file, w.getImageUrl(file, 300, 200))
//      largeUrls.put(file, w.getImageUrl(file, 1280, 1024))
//    }

    KOATUU.load()
  }

  def loadFileCache(file:File): Map[String, String] = {
      val galleryUrlsProps = new Properties
      galleryUrlsProps.load(new FileReader(file))

    Logger.info("loadFileCache file " + file)
    Logger.info("loadFileCache size " + galleryUrlsProps.size())
    Logger.info("loadFileCache head " + galleryUrlsProps.asScala.head)

    galleryUrlsProps.asScala.toMap
  }

}