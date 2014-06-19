package controllers

import controllers.Admin._
import org.intracer.wmua.{User, Contest, Round}
import play.api.mvc.Controller

object Rounds extends Controller with Secured {

  def rounds() = withAuth({
    user =>
      implicit request =>
        val rounds = Round.findByContest(user.contest)
        val contest = Contest.byId(user.contest).get

        Ok(views.html.rounds(user, rounds, editRoundForm,
          imagesForm.fill(Some(contest.getImages)),
          selectRoundForm.fill(contest.currentRound.toString),
          Round.current(user)))
  }, Set(User.ADMIN_ROLE))

  def roundStat() = withAuth({
    user =>
      implicit request =>
        val rounds = Round.findByContest(user.contest)
        val contest = Contest.byId(user.contest).get

        Ok(views.html.roundStat(user, Round.current(user)))
  }, Set(User.ADMIN_ROLE) ++ User.ORG_COM_ROLES)

}
