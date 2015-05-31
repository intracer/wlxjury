package controllers

import akka.actor.ActorSystem
import org.intracer.wmua._
import org.scalawiki.MwBot
import org.scalawiki.dto.Page
import org.scalawiki.http.HttpClientImpl
import org.scalawiki.query.SinglePageQuery
import org.scalawiki.wikitext.TemplateParser
import org.scalawiki.wlx.dto.Contest
import org.scalawiki.wlx.query.MonumentQuery

import scala.concurrent.{Await, Future}

object GlobalRefactor {

  val system = ActorSystem()

  val http = new HttpClientImpl(system)

  val commons = MwBot.get(MwBot.commons)

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

  def appendImages(category: String, contest: ContestJury, idsFilter: Set[String] = Set.empty): Any = {
    val images = ImageJdbc.findByContest(contest.id)

    val query = commons.page(category)
    initImagesFromCategory(contest, query, images, idsFilter)
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
      val ukWiki = new MwBot(http, system, "uk.wikipedia.org", None)

      Await.result(ukWiki.login("***REMOVED***", "***REMOVED***"), http.timeout)
      //    listsNew(system, http, ukWiki)

      val monumentQuery = MonumentQuery.create(contest)
      val monuments = monumentQuery.byMonumentTemplate()

      MonumentJdbc.batchInsert(monuments)
    }

  }

  def initImagesFromCategory(
                              contest: ContestJury,
                              query: SinglePageQuery,
                              existing: Seq[Image],
                              idsFilter: Set[String] = Set.empty): Future[Unit] = {
    val existingPageIds = existing.map(_.pageId).toSet

    //bot.page("User:***REMOVED***/embeddedin").imageInfoByGenerator("images", "im", props = Set("timestamp", "user", "size", "url"), titlePrefix = Some(""))
    query.imageInfoByGenerator("categorymembers", "cm", props = Set("timestamp", "user", "size", "url"), titlePrefix = None).map {
      filesInCategory =>
        val newImagesOrigIds: Seq[Image] = filesInCategory.flatMap(page => ImageJdbc.fromPage(page, contest)).sortBy(_.pageId).filterNot(i => existingPageIds.contains(i.pageId))

//        val maxId = newImagesOrigIds.map(_.pageId).max * 4024
//
//        val newImages = newImagesOrigIds.map(p => p.copy(pageId = maxId + p.pageId))

        contest.monumentIdTemplate.fold(saveNewImages(contest, newImagesOrigIds)) { monumentIdTemplate =>

          query.revisionsByGenerator("categorymembers", "cm",
            Set.empty, Set("content", "timestamp", "user", "comment"), limit = "50", titlePrefix = None) map {
            pages =>

              val idRegex = """(\d\d)-(\d\d\d)-(\d\d\d\d)"""
              val ids: Seq[String] = monumentIds(pages, existingPageIds, monumentIdTemplate)

              val imagesWithIds = newImagesOrigIds.zip(ids).map {
                case (image, id) => image.copy(monumentId = Some(id))
              }.filter(_.monumentId.forall(id => idsFilter.isEmpty || idsFilter.contains(id)))
              saveNewImages(contest, imagesWithIds)
          }
        }
    }
  }

  def defaultParam(text: String, templateName: String): Option[String] =
    TemplateParser.parseOne(text, Some(templateName)).flatMap(_.getParamOpt("1"))

  def monumentIds(pages: Seq[Page], existingPageIds: Set[Long], monumentIdTemplate: String): Seq[String] = {

    pages.sortBy(_.id).filterNot(i => existingPageIds.contains(i.id.get)).map {
      page =>
        page.text.flatMap(text => defaultParam(text, monumentIdTemplate))
          //                .map(id => if (id.matches(idRegex)) Some(id) else Some(id))
          .map(id => if (id.length < 100) id else id.substring(0, 100)).getOrElse("")
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
