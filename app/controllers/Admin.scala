package controllers

import play.api.mvc.Controller
import org.intracer.wmua.User

object Admin extends Controller with Secured {

  def users() = withAuth {
    username =>
      implicit request =>
        val user = User.byUserName.get(username.trim).get
        val users = User.allUsers

        Ok(views.html.users(user, users))
  }

}
