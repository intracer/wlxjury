package controllers

import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import org.intracer.wmua.User

object Application extends Controller {

  def index = Action {
    implicit request =>

      Ok(views.html.index(Application.loginForm))
  }

  def auth() = Action {
    implicit request =>

    loginForm.bindFromRequest.fold(
      formWithErrors => // binding failure, you retrieve the form containing errors,
        BadRequest(views.html.index(formWithErrors)),
      value => // binding success, you get the actual value
        Redirect(routes.Gallery.list(1)).withSession(Security.username -> value._1))
  }

  /**
   * Logout and clean the session.
   *
   * @return Index page
   */
  def logout = Action {
    //      session.data = Map()
    Redirect(routes.Application.index()).withNewSession
  }

  val loginForm = Form(
    tuple(
      "login" -> nonEmptyText(),
      "password" -> nonEmptyText()
    ) verifying("invalid.user.or.password", fields => fields match {
          case (l, p) => User.login(l,p).isDefined
      })
  )
}



