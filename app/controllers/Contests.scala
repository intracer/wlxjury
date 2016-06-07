package controllers

import db.scalikejdbc.{ContestJuryJdbc, ImageJdbc}
import org.intracer.wmua.cmd.ImageInfoFromCategory
import org.intracer.wmua.{ContestJury, Image, User}
import org.scalawiki.wlx.CountryParser
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages.Implicits._
import play.api.mvc.Controller
import spray.util.pimpFuture

object Contests extends Controller with Secured {

  def list() = withAuth({
    user =>
      implicit request =>
        val contests = findContests

        Ok(views.html.contests(user, contests, editContestForm, importForm))
  }, User.ADMIN_ROLES)

  def findContests: List[ContestJury] = {
    val messages = applicationMessages
    val contests = ContestJuryJdbc.findAll()
    //.map(_.copy(messages = messages))
    contests
  }

  def saveContest() = withAuth({
    user =>
      implicit request =>

        editContestForm.bindFromRequest.fold(
          formWithErrors => {
            val contests = findContests
            BadRequest(views.html.contests(user, contests, formWithErrors, importForm))
          },
          formContest => {
            createContest(formContest)
            Redirect(routes.Contests.list())
          })
  }, Set(User.ROOT_ROLE))

  def importContests() = withAuth({
    user =>
      implicit request =>

        importForm.bindFromRequest.fold(
          formWithErrors => {
            val contests = findContests
            BadRequest(views.html.contests(user, contests, editContestForm, formWithErrors))
          },
          formContest => {
            val wiki = Global.commons.pageText(formContest).await
            val imported = CountryParser.parse(wiki)
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
  }, Set(User.ROOT_ROLE))

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

  def images(contestId: Long) = withAuth({
    user =>
      implicit request =>
        val contest = ContestJuryJdbc.byId(contestId).get

        val sourceImageNum = getNumberOfImages(contest)
        val dbImagesNum = ImageJdbc.countByContest(contestId)

        val imageInfos = Seq.empty[Image] //imageInfo.apply().await

        val filledForm = importForm.fill(contest.images.getOrElse(""))
        Ok(views.html.contest_images(filledForm, contest, user, sourceImageNum, dbImagesNum))
  }, User.ADMIN_ROLES)

  def getNumberOfImages(contest: ContestJury): Long = {
    val imageInfo = ImageInfoFromCategory(contest.images.get, contest, Global.commons)

    imageInfo.numberOfImages.await
  }

  def importImages(contestId: Long) = withAuth({ user =>
    implicit request =>
      val contest = ContestJuryJdbc.byId(contestId).get

      val sourceImageNum = getNumberOfImages(contest)

      Global.progressController.foreach(_.max = sourceImageNum)

      new GlobalRefactor(Global.commons).appendImages(
        contest.images.get,
        contest
      )

      Redirect(routes.Contests.images(contestId))
  }, User.ADMIN_ROLES)

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

  val importForm = Form(
    single(
      "source" -> nonEmptyText
    )
  )

}

