package controllers

import db.scalikejdbc.{CategoryJdbc, CategoryLinkJdbc, ContestJury, ImageJdbc, User}
import org.intracer.wmua.Image
import org.intracer.wmua.cmd.{FetchImageInfo, FetchImageText, ImageEnricher}
import org.intracer.wmua.cmd.FetchImageText.defaultParam
import org.scalawiki.MwBot
import org.scalawiki.wlx.dto.{Contest, Country}
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.Forms.tuple
import play.api.mvc.Controller
import play.api.i18n.Messages.Implicits._
import spray.util.pimpFuture
import play.api.Play.current

import javax.inject.Inject
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class ImagesController @Inject()(val commons: MwBot)(implicit ec: ExecutionContext) extends Controller with Secured {

  /**
    * Shows contest images view
    */
  def images(contestId: Long, inProgress: Boolean = false) = withAuth(contestPermission(User.ADMIN_ROLES, Some(contestId))) {
    user =>
      implicit request =>
        val contest = ContestJury.findById(contestId).get

        val sourceImageNum = getNumberOfImages(contest)
        val dbImagesNum = ImageJdbc.countByContest(contest)

        val filledForm = importImagesForm.fill((contest.images.getOrElse(""), "", ""))
        Ok(views.html.contest_images(filledForm, contest, user, sourceImageNum, dbImagesNum, inProgress))
  }

  def getNumberOfImages(contest: ContestJury): Long = {
    contest.images.fold(0L) { images =>
        FetchImageInfo(images, Seq.empty, contest, commons).numberOfImages.await
    }
  }

  /**
    * Imports images from commons
    */
  def importImages(contestId: Long) = withAuth(contestPermission(User.ADMIN_ROLES, Some(contestId))) { user =>
    implicit request =>
      val contest = ContestJury.findById(contestId).get
      importImagesForm.bindFromRequest.fold(
        formWithErrors => BadRequest(views.html.contest_images(formWithErrors, contest, user, 0, ImageJdbc.countByContest(contest))), {
          case (source, list, action) =>

            val withNewImages = contest.copy(images = Some(source))

            if (action == "import.images") {
              appendImages(source, list, withNewImages)
            } else if (action == "update.monuments") {
              updateImageMonuments(source, withNewImages)
            }

            Redirect(routes.ImagesController.images(contestId))
        })
  }

  def updateImageMonuments(source: String, contest: ContestJury): Unit = {
    if (contest.country == Country.Ukraine.name && contest.monumentIdTemplate.isDefined) {
      val monumentContest = Seq("earth", "monuments").filter(contest.name.toLowerCase.contains) match {
        case Seq("earth") => Some(Contest.WLEUkraine(contest.year))
        case Seq("monuments") => Some(Contest.WLMUkraine(contest.year))
        case _ => None
      }

      monumentContest.foreach(MonumentsController.updateLists)
    }

    def generatorParams: (String, String) = {
      if (source.toLowerCase.startsWith("category:")) {
        ("categorymembers", "cm")
      } else if (source.toLowerCase.startsWith("template:")) {
        ("embeddedin", "ei")
      }
      else {
        ("images", "im")
      }
    }

    val monumentIdTemplate = contest.monumentIdTemplate.get

    val (generator, prefix) = generatorParams

    commons.page(source).revisionsByGenerator(generator, prefix,
      Set.empty, Set("content", "timestamp", "user", "comment"), limit = "50", titlePrefix = None) map {
      pages =>

        val idRegex = """(\d\d)-(\d\d\d)-(\d\d\d\d)"""

        pages.foreach { page =>
          for (monumentId <- page.text.flatMap(text => defaultParam(text, monumentIdTemplate))
            .flatMap(id => if (id.matches(idRegex)) Some(id) else None);
               pageId <- page.id) {
            ImageJdbc.updateMonumentId(pageId, monumentId)
          }
        }
    } recover { case e: Exception => println(e) }
  }

  def appendImages(source: String, imageList: String, contest: ContestJury, idsFilter: Set[String] = Set.empty, max: Long = 0) = {
    ContestJury.setImagesSource(contest.getId, Some(source))
    val existingImages = ImageJdbc.findByContest(contest)
    initImagesFromSource(contest, source, imageList, existingImages, idsFilter, max)
  }

  def initImagesFromSource(contest: ContestJury,
                           source: String,
                           titles: String,
                           existing: Seq[Image],
                           idsFilter: Set[String] = Set.empty,
                           max: Long) = {
    val existingByPageId = existing.groupBy(_.pageId)
    val withImageDescriptions = contest.monumentIdTemplate.isDefined
    val titlesSeq: Seq[String] = if (titles.trim.isEmpty) {
      Nil
    } else {
      titles.split("(\r\n|\n|\r)")
    }

    val imageInfos = FetchImageInfo(source, titlesSeq, contest, commons, max).apply()

    val getImages = if (withImageDescriptions) {
      fetchImageDescriptions(contest, source, max, imageInfos)
    } else {
      imageInfos
    }

    val result = getImages.map { images =>

      val newImages =  images.filter(image => !existingByPageId.contains(image.pageId))

      val existingIds = ImageJdbc.existingIds(newImages.map(_.pageId).toSet).toSet

      val notInOtherContests = newImages.filterNot(image => existingIds.contains(image.pageId))

      val categoryId = CategoryJdbc.findOrInsert(source)
      saveNewImages(contest, notInOtherContests)
      CategoryLinkJdbc.addToCategory(categoryId, newImages)

      val updatedImages =  images.filter(image => existingByPageId.contains(image.pageId) && existingByPageId(image.pageId) != image)
      updatedImages.foreach(ImageJdbc.update)
    }

    Await.result(result, 500.minutes)
  }

  def fetchImageDescriptions(contest: ContestJury, source: String, max: Long, imageInfos: Future[Seq[Image]]): Future[Seq[Image]] = {
    val revInfo = FetchImageText(source, contest, contest.monumentIdTemplate, commons, max).apply()
    ImageEnricher.zipWithRevData(imageInfos, revInfo)
  }

  def saveNewImages(contest: ContestJury, imagesWithIds: Seq[Image]) = {
    println("saving images: " + imagesWithIds.size)

    ImageJdbc.batchInsert(imagesWithIds)
    println("saved images")
    //createJury()
    //    initContestFiles(contest, imagesWithIds)
  }

  val importImagesForm = Form(
    tuple(
      "source" -> text,
      "list" -> text,
      "action" -> text
    )
  )
}
