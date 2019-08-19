package controllers

import db.scalikejdbc.{ContestJuryJdbc, ImageJdbc}
import org.intracer.wmua.cmd.FetchImageInfo
import org.intracer.wmua.cmd.ImageTextFromCategory._
import org.intracer.wmua.{ContestJury, User}
import org.scalawiki.dto.Namespace
import org.scalawiki.wlx.dto.{Contest, ContestType, NoAdmDivision}
import org.scalawiki.wlx.{CampaignList, CountryParser}
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages.Implicits._
import play.api.mvc.Controller
import spray.util.pimpFuture
import javax.inject.Inject
import org.scalawiki.MwBot
import play.api.Logger

import scala.concurrent.Future

class Contests @Inject()(val commons: MwBot) extends Controller with Secured {

  def fetchContests(contestType: Option[String], year: Option[Int], country: Option[String]): Future[Seq[Contest]] = {
    if (contestType.isEmpty) {
      CampaignList.yearsContests()
    } else {
      (for (ct <- contestType; y <- year) yield {
        val contest = Contest(ContestType.byCode(ct).get, NoAdmDivision(), y)
        CampaignList.contestsFromCategory(contest.imagesCategory)
      }).getOrElse(Future.successful(Seq.empty[Contest]))
    }
  }

  def list(contestType: Option[String], year: Option[Int], country: Option[String]) = withAuth(rolePermission(Set(User.ROOT_ROLE))) {
    user =>
      implicit request =>
        val contests = findContests
        val filtered = contests.filter { c =>
          contestType.forall(c.name.equals) && year.forall(c.year.equals)
        }

        Ok(views.html.contests(user, contests, filtered, editContestForm, importContestsForm, contestType, year))
  }

  def findContests: List[ContestJury] = {
    ContestJuryJdbc.findAll() //.map(_.copy(messages = applicationMessages))
  }

  def saveContest() = withAuth(rolePermission(Set(User.ROOT_ROLE))) {
    user =>
      implicit request =>

        editContestForm.bindFromRequest.fold(
          formWithErrors => {
            val contests = findContests
            BadRequest(views.html.contests(user, contests, Seq.empty, formWithErrors, importContestsForm))
          },
          formContest => {
            createContest(formContest)
            Redirect(routes.Contests.list())
          })
  }

  def importContests() = withAuth(rolePermission(Set(User.ROOT_ROLE))) {
    user =>
      implicit request =>

        importContestsForm.bindFromRequest.fold(
          formWithErrors => {
            val contests = findContests
            BadRequest(views.html.contests(user, contests, Seq.empty, editContestForm, formWithErrors))
          },
          formContest => {
            val imported =
              if (formContest.startsWith("Commons:")) {
                importListPage(formContest)
              } else {
                importCategory(formContest)
              }

            val existing = ContestJuryJdbc.findAll().map(c => s"${c.name}/${c.year}/${c.country}").toSet
            val newContests = imported.filterNot(c => existing.contains(s"${c.contestType.name}/${c.year}/${c.country.name}"))

            newContests.foreach {
              contest =>
                val contestJury = ContestJury(
                  id = None,
                  name = contest.contestType.name,
                  year = contest.year,
                  country = contest.country.name,
                  images = Some(s"Category:Images from ${contest.contestType.name} ${contest.year} in ${contest.country.name}"),
                  monumentIdTemplate = contest.uploadConfigs.headOption.map(_.fileTemplate)
                )
                createContest(contestJury)
            }
            Redirect(routes.Contests.list())
          })
  }

  def importListPage(pageName: String): Seq[Contest] = {
    val wiki = commons.pageText(pageName).await
    CountryParser.parse(wiki)
  }

  def importCategory(categoryName: String): Seq[Contest] = {
    val pages = commons.page(categoryName).categoryMembers(Set(Namespace.CATEGORY)).await

    pages.flatMap(p => CountryParser.fromCategoryName(p.title)) ++
      CountryParser.fromCategoryName(categoryName).filter(_.country.name.nonEmpty)
  }

  def createContest(contest: ContestJury): ContestJury = {
    ContestJuryJdbc.create(
      contest.id,
      contest.name,
      contest.year,
      contest.country,
      contest.images,
      contest.categoryId,
      contest.currentRound,
      contest.monumentIdTemplate
    )
  }

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

    if (contest.monumentIdTemplate.isDefined) {
      val monumentContest = Contest.WLEUkraine(contest.year)
      Monuments.updateLists(monumentContest)
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

  def regions(contestId: Long): Map[String, String] = {
    ContestJuryJdbc.findById(contestId)
      .filter(_.country == "Ukraine")
      .map(_ => KOATUU.regions)
      .getOrElse(Map.empty)
  }

  val editContestForm = Form(
    mapping(
      "id" -> optional(longNumber),
      "name" -> nonEmptyText,
      "year" -> number,
      "country" -> nonEmptyText,
      "images" -> optional(text),
      "currentRound" -> optional(longNumber),
      "monumentIdTemplate" -> optional(text),
      "greetingText" -> optional(text),
      "useGreeting" -> boolean
    )(
      (id, name, year, country, images, currentRound, monumentIdTemplate, greetingText, useGreeting) =>
        ContestJury(id, name, year, country, images, None, currentRound, monumentIdTemplate, Greeting(greetingText, useGreeting)))
    ((c: ContestJury) =>
      Some(c.id, c.name, c.year, c.country, c.images, c.currentRound, c.monumentIdTemplate, c.greeting.text, c.greeting.use))
  )

  val importContestsForm = Form(
    single(
      "source" -> nonEmptyText
    )
  )

  val importImagesForm = Form(
    tuple(
      "source" -> text,
      "list" -> text,
      "action" -> text
    )
  )
}