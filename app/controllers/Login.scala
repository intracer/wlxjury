package controllers

import db.scalikejdbc.{UserJdbc, RoundJdbc}
import org.intracer.wmua.{Round, User}
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.Lang
import play.api.mvc._
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api.mvc.Results._

object Login extends Controller with Secured {

  def index = withAuth {
    user =>
      implicit request =>
        indexRedirect(user)
  }

  def indexRedirect(user: User): Result = {
    if (user.roles.contains(User.ORG_COM_ROLES.head)) {
      Redirect(routes.Rounds.currentRoundStat())
    } else if (user.roles.contains(User.JURY_ROLES.head)) {
      val round = RoundJdbc.current(user)
      if (round.rates == Round.binaryRound) {
        Redirect(routes.Gallery.list(user.id.get, 0, "all", round.id.get))
      } else {
        Redirect(routes.Gallery.byRate(user.id.get, 0, "all", 0))
      }
    } else if (User.ADMIN_ROLES.intersect(user.roles).nonEmpty){
      Redirect(routes.Admin.users())
    } else {
      Redirect(
        routes.Login.error("You don't have permission to access this page")
      )
    }
  }

  def login = Action {
    implicit request =>
      Ok(views.html.index(loginForm))
  }

  def auth() = Action {
    implicit request =>

      loginForm.bindFromRequest.fold(
        formWithErrors => // binding failure, you retrieve the form containing errors,
          BadRequest(views.html.index(formWithErrors)),
        value => {
          // binding success, you get the actual value
          val user = UserJdbc.login(value._1, value._2).get
          val result = indexRedirect(user).withSession(Security.username -> value._1.trim)
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
    Redirect(routes.Login.login()).withNewSession
  }

  def error(message: String) = withAuth {
    user =>
      implicit request =>
        Ok(views.html.error(message, user, user.id.get, user))
  }

  val loginForm = Form(
    tuple(
      "login" -> nonEmptyText(),
      "password" -> nonEmptyText()
    ) verifying("invalid.user.or.password", fields => fields match {
      case (l, p) => UserJdbc.login(l, p).isDefined
    })
  )
}



