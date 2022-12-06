package controllers

import db.scalikejdbc.{ContestJuryJdbc, User}
import org.intracer.wmua.ContestJury
import org.scalawiki.MwBot
import org.scalawiki.dto.Namespace
import org.scalawiki.wlx.dto.{Contest, ContestType, NoAdmDivision}
import org.scalawiki.wlx.{CampaignList, CountryParser}
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms.{optional, _}
import play.api.i18n.Messages.Implicits._
import play.api.mvc.Controller
import spray.util.pimpFuture

import javax.inject.Inject
import scala.concurrent.Future

class ContestsController @Inject()(val commons: MwBot) extends Controller with Secured {

  def fetchContests(contestType: Option[String], year: Option[Int], country: Option[String]): Future[Seq[Contest]] = {
    if (contestType.isEmpty) {
      CampaignList.yearsContests()
    } else {
      (for (ct <- contestType; y <- year) yield {
        val contest = Contest(ContestType.byCode(ct).get, NoAdmDivision, y)
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
            Redirect(routes.ContestsController.list())
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
                  monumentIdTemplate = contest.uploadConfigs.headOption.map(_.fileTemplate),
                  campaign = Some(contest.campaign)
                )
                createContest(contestJury)
            }
            Redirect(routes.ContestsController.list())
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

  def getContest(id: Long): Option[ContestJury] = {
    ContestJuryJdbc.findById(id)
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
      "useGreeting" -> boolean,
      "campaign" -> optional(text),
    )(
      (id, name, year, country, images, currentRound, monumentIdTemplate, greetingText, useGreeting, campaign) =>
        ContestJury(id, name, year, country, images, None, currentRound, monumentIdTemplate, Greeting(greetingText, useGreeting), campaign))
    ((c: ContestJury) =>
      Some(c.id, c.name, c.year, c.country, c.images, c.currentRound, c.monumentIdTemplate, c.greeting.text, c.greeting.use, c.campaign))
  )

  val importContestsForm = Form(
    single(
      "source" -> nonEmptyText
    )
  )
}