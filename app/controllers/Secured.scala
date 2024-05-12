package controllers

import controllers.Secured.UserName
import db.scalikejdbc.{Round, User}
import play.api.mvc._

/** Base trait for secured controllers
  */
abstract class Secured(cc: ControllerComponents) extends AbstractController(cc) {

  type Permission = User => Boolean

  /** @param request
    *   HTTP request with username set in session
    * @return
    *   optional user from database
    */
  def userFromRequest(request: RequestHeader): Option[User] = {
    request.session
      .get(UserName)
      .map(_.trim.toLowerCase)
      .flatMap(User.byUserName)
  }

  def onUnAuthenticated(request: RequestHeader): Result =
    Results.Redirect(routes.LoginController.login())

  def onUnAuthorized(user: User): Result =
    Results.Redirect(routes.LoginController.error("You don't have permission to access this page"))

  def withAuth(
      permission: Permission = rolePermission(User.ADMIN_ROLES ++ Set("jury", "organizer"))
  )(f: => User => Request[AnyContent] => Result): EssentialAction = {
    Security.Authenticated(userFromRequest, onUnAuthenticated) { user =>
      Action { request =>
        if (permission(user))
          f(user)(request)
        else
          onUnAuthorized(user)
      }
    }
  }

  def rolePermission(roles: Set[String])(user: User): Boolean = user.hasAnyRole(roles)

  def isRoot(user: User): Boolean = rolePermission(Set(User.ROOT_ROLE))(user)

  def contestPermission(roles: Set[String], contestId: Option[Long])(user: User): Boolean = {
    isRoot(user) ||
    (user.hasAnyRole(roles) && user.isInContest(contestId))
  }

  def roundPermission(roles: Set[String], roundId: Long)(user: User): Boolean =
    Round.findById(roundId).exists { round =>
      contestPermission(roles, Some(round.contestId))(user)
    }
}

object Secured {
  val UserName = "username"
}
