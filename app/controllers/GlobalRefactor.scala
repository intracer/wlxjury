package controllers

import akka.actor.ActorSystem
import client.dto.{Namespace, SinglePageQuery, Template}
import client.wlx.dto.Contest
import client.wlx.query.MonumentQuery
import client.{HttpClientImpl, MwBot}
import org.intracer.wmua._

import scala.concurrent.{Await, Future}

object GlobalRefactor {

  val system = ActorSystem()

  val http = new HttpClientImpl(system)

  val commons = new MwBot(http, system, "commons.wikimedia.org")

  import controllers.GlobalRefactor.system.dispatcher

  def initContest(category: String, contest: ContestJury): Any = {
    val images = ImageJdbc.findByContest(contest.id)

    if (images.isEmpty) {
      val query = commons.page(category)
      //PageQuery.byId(category.pageid)
      initImagesFromCategory(contest, query, Seq.empty)
    } else {
      //        initContestFiles(contest, images)
      //createJury()
    }
  }

  def appendImages(category: String, contest: ContestJury): Any = {
    val images = ImageJdbc.findByContest(contest.id)

      val query = commons.page(category)
      initImagesFromCategory(contest, query, images)
  }

  def createJury() {
    //    val matt = User.findByEmail("***REMOVED***")
    //
    //    if (matt.isEmpty) {
    //      for (user <- UkrainianJury.users) {
    //        Admin.createNewUser(User.wmuaUser, user)
    //      }
    //    }
    val selection = Selection.findAll()
    if (selection.isEmpty) {
      //Admin.distributeImages(Contest.byId(14).get)
    }
  }

  def initLists(contest: Contest) = {

    if (true || MonumentJdbc.findAll().isEmpty) {
      val ukWiki = new MwBot(http, system, "uk.wikipedia.org")

      Await.result(ukWiki.login("***REMOVED***", "***REMOVED***"), http.timeout)
      //    listsNew(system, http, ukWiki)

      val monumentQuery = MonumentQuery.create(contest)
      val monuments = monumentQuery.byMonumentTemplate()

      MonumentJdbc.batchInsert(monuments)
    }

  }

  def initImagesFromCategory(contest: ContestJury, query: SinglePageQuery, existing: Seq[Image]): Future[Unit] = {
    val existingPageIds = existing.map(_.pageId)

    query.imageInfoByGenerator("categorymembers", "cm", Set(Namespace.FILE), props = Set("timestamp", "user", "size", "url"), titlePrefix = None).map {
      filesInCategory =>
        val newImages: Seq[Image] = filesInCategory.flatMap(page => ImageJdbc.fromPage(page, contest)).sortBy(_.pageId).filterNot(i => existingPageIds.contains(i.pageId))

        contest.monumentIdTemplate.fold(saveNewImages(contest, newImages)) { monumentIdTemplate =>
          query.revisionsByGenerator("categorymembers", "cm",
            Set.empty, Set("content", "timestamp", "user", "comment"), titlePrefix = None) map {
            pages =>

              val idRegex = """(\d\d)-(\d\d\d)-(\d\d\d\d)"""
              val ids: Seq[String] = pages.sortBy(_.pageid).filterNot(i => existingPageIds.contains(i.pageid))
                .map(_.text.map(Template.getDefaultParam(_, monumentIdTemplate))
                //                .map(id => if (id.matches(idRegex)) Some(id) else Some(id))
                .map(id => if (id.size < 100) id else id.substring(0, 100)).getOrElse(""))

              val imagesWithIds = newImages.zip(ids).map {
                case (image, id) => image.copy(monumentId = Some(id))
              }
              saveNewImages(contest, imagesWithIds)
          }
        }
    }
  }

  def saveNewImages(contest: ContestJury, imagesWithIds: Seq[Image]) = {
    ImageJdbc.batchInsert(imagesWithIds)
    createJury()
    //    initContestFiles(contest, imagesWithIds)
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


}
