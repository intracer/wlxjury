package controllers

import db.scalikejdbc.{ContestJuryJdbc, ImageJdbc, User}
import org.intracer.wmua.ContestJury
import org.intracer.wmua.cmd.FetchImageInfo
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

class ImagesController @Inject()(val commons: MwBot) extends Controller with Secured {

  /**
    * Shows contest images view
    */
  def images(contestId: Long, inProgress: Boolean = false) = withAuth(contestPermission(User.ADMIN_ROLES, Some(contestId))) {
    user =>
      implicit request =>
        val contest = ContestJuryJdbc.findById(contestId).get

        val sourceImageNum = getNumberOfImages(contest)
        val dbImagesNum = ImageJdbc.countByContest(contest)

        val filledForm = importImagesForm.fill((contest.images.getOrElse(""), "", ""))
        Ok(views.html.contest_images(filledForm, contest, user, sourceImageNum, dbImagesNum, inProgress))
  }

  def getNumberOfImages(contest: ContestJury): Long = {
    contest.images.fold(0L) {
      images =>
        FetchImageInfo(images, Seq.empty, contest, commons).numberOfImages.await
    }
  }

  /**
    * Imports images from commons
    */
  def importImages(contestId: Long) = withAuth(contestPermission(User.ADMIN_ROLES, Some(contestId))) { user =>
    implicit request =>
      val contest = ContestJuryJdbc.findById(contestId).get
      importImagesForm.bindFromRequest.fold(
        formWithErrors => BadRequest(views.html.contest_images(formWithErrors, contest, user, 0, ImageJdbc.countByContest(contest))), {
          case (source, list, action) =>

            val withNewImages = contest.copy(images = Some(source))

            if (action == "import.images") {
              new GlobalRefactor(commons).appendImages(source, list, withNewImages)
            } else if (action == "update.monuments") {
              updateImageMonuments(source, withNewImages)
            }

            Redirect(routes.Contests.images(contestId))
        })
  }


  def updateImageMonuments(source: String, contest: ContestJury): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global

    if (contest.country == Country.Ukraine.name && contest.monumentIdTemplate.isDefined) {
      val monumentContest = Seq("earth", "monuments").filter(contest.name.toLowerCase.contains) match {
        case Seq("earth") => Some(Contest.WLEUkraine(contest.year))
        case Seq("monuments") => Some(Contest.WLMUkraine(contest.year))
        case _ => None
      }

      monumentContest.foreach(Monuments.updateLists)
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

  val importImagesForm = Form(
    tuple(
      "source" -> text,
      "list" -> text,
      "action" -> text
    )
  )
}
