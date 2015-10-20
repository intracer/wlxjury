package controllers

import db.scalikejdbc.ContestJuryJdbc
import org.intracer.wmua.User
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api.mvc.Controller

object Contests extends Controller with Secured {

  def list() = withAuth({
    user =>
      implicit request =>
        val messages = applicationMessages
        val contests = ContestJuryJdbc.findAll()//.map(_.copy(messages = messages))

        Ok(views.html.contests(user, contests))
  }, User.ADMIN_ROLES)

}
