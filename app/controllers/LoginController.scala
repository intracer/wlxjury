package controllers

import db.scalikejdbc.{Round, User}
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.{I18nSupport, Lang}
import play.api.mvc._

import javax.inject.Inject

class LoginController @Inject()(cc: ControllerComponents,val admin: UsersController)
    extends Secured(cc)
    with I18nSupport {

  def index = withAuth() { user => implicit request =>
    indexRedirect(user)
  }

  def indexRedirect(user: User): Result = {
    if (user.hasAnyRole(User.ORG_COM_ROLES)) {
      Redirect(routes.RoundsController.currentRoundStat())
    } else if (user.hasAnyRole(User.JURY_ROLES)) {
      val maybeRound = Round.activeRounds(user).headOption
      maybeRound.fold {
        Redirect(routes.LoginController.error("no.round.yet"))
      } { round =>
        if (round.isBinary) {
          Redirect(
            routes.GalleryController
              .list(user.getId, 1, "all", round.getId, rate = Some(0)))
        } else {
          Redirect(routes.GalleryController.byRate(user.getId, 0, "all", 0))
        }
      }
    } else if (user.hasRole(User.ROOT_ROLE)) {
      Redirect(routes.ContestsController.list())
    } else if (user.hasAnyRole(User.ADMIN_ROLES)) {
      Redirect(routes.UsersController.users(user.contestId))
    } else {
      Redirect(
        routes.LoginController.error(
          "You don't have permission to access this page"))
    }
  }

  def login = Action { implicit request =>
    val users = User.count()
    if (users > 0) {
      Ok(views.html.index(loginForm))
    } else {
      Ok(views.html.signUp(signUpForm))
    }
  }

  def auth() = Action { implicit request =>
    loginForm.bindFromRequest().fold(
      formWithErrors => BadRequest(views.html.index(formWithErrors)), {
        case (login, password) =>
          val user = User.login(login, password).get
          val result =
            indexRedirect(user).withSession("username" -> login)
          user.lang.fold(result)(l => result.withLang(Lang(l)))
      }
    )
  }

  def signUpView() = Action { implicit request =>
    Ok(views.html.signUp(signUpForm))
  }

  def signUp() = Action { implicit request =>
    signUpForm.bindFromRequest().fold(
      formWithErrors => BadRequest(views.html.signUp(formWithErrors)), {
        case (login: String, password: String, _) =>
          val users = User.count()
          val roles =
            if (users > 0)
              Set.empty[String]
            else
              Set(User.ROOT_ROLE)

          val newUser = new User(fullname = "",
                                 email = login,
                                 password = Some(password),
                                 roles = roles)

          val user = admin.createNewUser(newUser, newUser)
          val result =
            indexRedirect(user).withSession("username" -> password)
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
    Redirect(routes.LoginController.login()).withNewSession
  }

  def error(message: String) = withAuth() { user => implicit request =>
    Ok(views.html.error(message, user, user.getId))
  }

  val loginForm = Form(
    tuple(
      "login" -> nonEmptyText(),
      "password" -> nonEmptyText()
    ) verifying ("invalid.user.or.password", fields =>
      fields match {
        case (l, p) => User.login(l, p).isDefined
    })
  )

  val signUpForm = Form(
    tuple(
      "login" -> nonEmptyText(),
      "password" -> nonEmptyText(),
      "repeat.password" -> nonEmptyText()
    ) verifying ("invalid.user.or.password", fields =>
      fields match {
        case (_, p1, p2) => p1 == p2
    })
  )

}
