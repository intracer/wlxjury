package controllers

import db.scalikejdbc.{ContestJuryJdbc, ImageJdbc}
import org.intracer.wmua.cmd.FetchImageInfo
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

import scala.concurrent.Future

object Contests extends Controller with Secured {

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
        val fetched = Seq.empty // fetchContests(contestType, year, country).await

        Ok(views.html.contests(user, contests, fetched, editContestForm, importContestsForm))
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
            imported.foreach {
              contest =>
                val contestJury = new ContestJury(
                  id = None,
                  name = contest.contestType.name,
                  year = contest.year,
                  country = contest.country.name,
                  images = Some(s"Category:Images from ${contest.contestType.name} ${contest.year} in ${contest.country.name}")
                )
                createContest(contestJury)
            }
            Redirect(routes.Contests.list())
          })
  }

  def importListPage(formContest: String): Seq[Contest] = {
    val wiki = Global.commons.pageText(formContest).await
    CountryParser.parse(wiki)
  }

  def importCategory(formContest: String): Seq[Contest] = {
    val pages = Global.commons.page(formContest).categoryMembers(Set(Namespace.CATEGORY)).await

    pages.flatMap(p => CountryParser.fromCategoryName(p.title)) ++
      CountryParser.fromCategoryName(formContest).filter(_.country.name.nonEmpty)
  }

  def createContest(contest: ContestJury): ContestJury = {
    ContestJuryJdbc.create(
      contest.id,
      contest.name,
      contest.year,
      contest.country,
      contest.images,
      contest.currentRound,
      contest.monumentIdTemplate
    )
  }

  def images(contestId: Long, inProgress: Boolean = false) = withAuth(contestPermission(User.ADMIN_ROLES, Some(contestId))) {
    user =>
      implicit request =>
        val contest = ContestJuryJdbc.findById(contestId).get

        val sourceImageNum = getNumberOfImages(contest)
        val dbImagesNum = ImageJdbc.countByContest(contestId)

        val filledForm = importImagesForm.fill((contest.images.getOrElse(""), ""))
        Ok(views.html.contest_images(filledForm, contest, user, sourceImageNum, dbImagesNum, inProgress))
  }

  def getNumberOfImages(contest: ContestJury): Long = {
    contest.images.fold(0L) {
      images =>
        FetchImageInfo(images, Seq.empty, contest, Global.commons).numberOfImages.await
    }
  }

  def importImages(contestId: Long) = withAuth(contestPermission(User.ADMIN_ROLES, Some(contestId))) { user =>
    implicit request =>
      val contest = ContestJuryJdbc.findById(contestId).get
      val dbImagesNum = ImageJdbc.countByContest(contestId)

      importImagesForm.bindFromRequest.fold(
        formWithErrors => {
          BadRequest(views.html.contest_images(formWithErrors, contest, user, 0, dbImagesNum))
        },
        tuple => {

          val (source, list) = tuple

          val withNewImages = contest.copy(images = Some(source))

          ContestJuryJdbc.updateImages(contestId, Some(source))

          val sourceImageNum = getNumberOfImages(withNewImages)

          new GlobalRefactor(Global.commons).appendImages(source, list, withNewImages, max = sourceImageNum)

          Redirect(routes.Contests.images(contestId))
        })
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
        ContestJury(id, name, year, country, images, currentRound, monumentIdTemplate, Greeting(greetingText, useGreeting)))
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
      "list" -> text
    )
  )

}

