package controllers

import org.intracer.wmua.{ContestJury, User}
import play.api.mvc.Controller

object Contests extends Controller with Secured {

  def list() = withAuth({
    user =>
      implicit request =>
        val contests = ContestJury.findAll().filter(_.year == 2015)

        Global.commons

        Ok(views.html.contests(user, contests))
  }, User.ORG_COM_ROLES)

}
