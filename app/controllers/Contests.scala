package controllers

import org.intracer.wmua.{ContestJury, User}
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api.mvc.Controller

object Contests extends Controller with Secured {

  def list() = withAuth({
    user =>
      implicit request =>
        val messages = applicationMessages
        val contests = ContestJury.findAll().map(_.copy(messages = messages))

        Ok(views.html.contests(user, contests))
  }, User.ORG_COM_ROLES)

}
