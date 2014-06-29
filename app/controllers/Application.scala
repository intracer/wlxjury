package controllers

import org.intracer.wmua.{Round, User}
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.Lang
import play.api.mvc._

object Application extends Controller with Secured {

  def index = withAuth {
    user =>
      implicit request =>

      Redirect(routes.Gallery.byRate(user.id.toInt, 0, "all", 0))
  }

  def login = Action {
    implicit request =>
      Ok(views.html.index(Application.loginForm))
  }

  def auth() = Action {
    implicit request =>

    loginForm.bindFromRequest.fold(
      formWithErrors => // binding failure, you retrieve the form containing errors,
        BadRequest(views.html.index(formWithErrors)),
      value => {
        // binding success, you get the actual value
        val user = User.login(value._1, value._2).get
        val round = Round.activeRounds.head
        val result = Redirect(routes.Gallery.byRate(user.id.toInt, 1, "all", round.id.toInt)).withSession(Security.username -> value._1.trim)
        import play.api.Play.current
        user.lang.fold(result)(l => result.withLang(Lang(l)))
      }
    )
  }

  /**
   * Logout and clean the session.
   *
   * @return Index page
   */
  def logout = Action {
    //      session.data = Map()
    Redirect(routes.Application.login()).withNewSession
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



