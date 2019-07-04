package controllers

import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.api.{LoginEvent, Silhouette}
import com.mohiva.play.silhouette.impl.providers.{CommonSocialProfileBuilder, SocialProvider, SocialProviderRegistry}
import javax.inject.Inject
import db.scalikejdbc.{RoundJdbc, UserJdbc}
import org.intracer.wmua.User
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.{I18nSupport, Lang, Langs, Messages}
import play.api.mvc._
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

class Login @Inject()(val admin: Admin,
                      components: ControllerComponents,
                      silhouette: Silhouette[DefaultEnv],
                      socialProviderRegistry: SocialProviderRegistry
                     )(
                       implicit
                       assets: AssetsFinder,
                       ex: ExecutionContext
                     ) extends AbstractController(components) with Secured with I18nSupport {

  def index = withAuth() { user =>
    implicit request =>
      indexRedirect(user)
  }

  def indexRedirect(user: User): Result = {
    if (user.hasAnyRole(User.ORG_COM_ROLES)) {
      Redirect(routes.Rounds.currentRoundStat())
    } else if (user.hasAnyRole(User.JURY_ROLES)) {
      val maybeRound = RoundJdbc.current(user).headOption
      maybeRound.fold {
        Redirect(routes.Login.error("no.round.yet"))
      } {
        round =>
          if (round.isBinary) {
            Redirect(routes.Gallery.list(user.getId, 1, "all", round.getId, rate = Some(0)))
          } else {
            Redirect(routes.Gallery.byRate(user.getId, 0, "all", 0))
          }
      }
    } else if (user.hasRole(User.ROOT_ROLE)) {
      Redirect(routes.Contests.list())
    } else if (user.hasAnyRole(User.ADMIN_ROLES)) {
      Redirect(routes.Admin.users(user.contestId))
    } else {
      Redirect(routes.Login.error("You don't have permission to access this page"))
    }
  }

  def login = silhouette.UnsecuredAction {
    implicit request =>
      val users = UserJdbc.count()
      if (users > 0) {
        Ok(views.html.index(loginForm, socialProviderRegistry))
      } else {
        Ok(views.html.signUp(signUpForm))
      }
  }

  def auth() = silhouette.UnsecuredAction { implicit request =>

    loginForm.bindFromRequest.fold(
      formWithErrors =>
        BadRequest(views.html.index(formWithErrors, socialProviderRegistry)), {
        case (login, password) =>
          val user = UserJdbc.login(login, password).get
          val result = indexRedirect(user).withSession(Security.username -> login)
          user.lang.fold(result)(l => result.withLang(Lang(l)))
      }
    )
  }

  /**
    * Authenticates a user against a social provider.
    *
    * @param provider The ID of the provider to authenticate against.
    * @return The result to display.
    */
  def socialAuth(provider: String) = Action.async { implicit request: Request[AnyContent] =>
    (socialProviderRegistry.get[SocialProvider](provider) match {
      case Some(p: SocialProvider with CommonSocialProfileBuilder) =>
        p.authenticate().flatMap {
          case Left(result) => Future.successful(result)
          case Right(authInfo) =>
            Future.failed(new ProviderException(s"Cannot authenticate with social provider $provider - not yet implemented"))
          //            for {
          //            profile <- p.retrieveProfile(authInfo)
          //            user <- userService.save(profile)
          //            authInfo <- authInfoRepository.save(profile.loginInfo, authInfo)
          //            authenticator <- silhouette.env.authenticatorService.create(profile.loginInfo)
          //            value <- silhouette.env.authenticatorService.init(authenticator)
          //            result <- silhouette.env.authenticatorService.embed(value, indexRedirect(user))
          //          } yield {
          //            silhouette.env.eventBus.publish(LoginEvent(user, request))
          //            result
          //          }
        }
      case _ => Future.failed(new ProviderException(s"Cannot authenticate with unexpected social provider $provider"))
    }).recover {
      case e: ProviderException =>
        Logger.error("Unexpected provider error", e)
        Redirect(routes.Login.index()).flashing("error" -> Messages("could.not.authenticate"))
    }
  }


  def signUpView() = silhouette.UnsecuredAction { implicit request =>
    Ok(views.html.signUp(signUpForm))
  }

  def signUp() = silhouette.UnsecuredAction { implicit request =>

    signUpForm.bindFromRequest.fold(
      formWithErrors =>
        BadRequest(views.html.signUp(formWithErrors)), {
        case (login: String, password: String, _) =>

          val users = UserJdbc.count()
          val roles = if (users > 0)
            Set.empty[String]
          else
            Set(User.ROOT_ROLE)

          val newUser = new User(fullname = "", email = login, password = Some(password), roles = roles)

          val user = admin.createNewUser(newUser, newUser)(request2Messages(request).lang)
          val result = indexRedirect(user).withSession(Security.username -> password)
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
    Redirect(routes.Login.login()).withNewSession
  }

  def error(message: String) = withAuth() {
    user =>
      implicit request =>
        Ok(views.html.error(message, user, user.getId))
  }

  val loginForm = Form(
    tuple(
      "login" -> nonEmptyText,
      "password" -> nonEmptyText
    ) verifying("invalid.user.or.password", fields => fields match {
      case (l, p) => UserJdbc.login(l, p).isDefined
    })
  )

  val signUpForm = Form(
    tuple(
      "login" -> nonEmptyText,
      "password" -> nonEmptyText,
      "repeat.password" -> nonEmptyText
    ) verifying("invalid.user.or.password", fields => fields match {
      case (_, p1, p2) => p1 == p2
    })
  )

}



