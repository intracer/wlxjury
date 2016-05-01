package controllers

import db.scalikejdbc.{ContestJuryJdbc, RoundJdbc}
import org.intracer.wmua.{ContestJury, User}
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages.Implicits._
import play.api.mvc.Controller

object Contests extends Controller with Secured {

  def list() = withAuth({
    user =>
      implicit request =>
        val contests = findContests

        Ok(views.html.contests(user, contests, editContestForm))
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
            BadRequest(views.html.contests(user, contests, formWithErrors))
          },
          formContest => {
            ContestJuryJdbc.create(
              formContest.id,
              formContest.name,
              formContest.year,
              formContest.country,
              formContest.images,
              formContest.currentRound,
              formContest.monumentIdTemplate
            )
            Redirect(routes.Contests.list())
          })
          })

  val editContestForm = Form(
    mapping(
      "id" -> optional(longNumber),
      "name" -> nonEmptyText,
      "year" -> number,
      "country" -> nonEmptyText,
      "images" -> optional(text),
      "currentRound" -> optional(longNumber),
      "monumentIdTemplate" -> optional(text)
    )(ContestJury.apply)(ContestJury.unapply)
  )

}

