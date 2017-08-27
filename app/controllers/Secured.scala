package controllers

import db.scalikejdbc.{RoundJdbc, UserJdbc}
import org.intracer.wmua.User
import play.api.mvc._

/**
  * Base trait for secured controllers
  */
trait Secured {

  type Permission = User => Boolean

  /**
    * @param request HTTP request with username set in session
    * @return optional user from database
    */
  def userFromRequest(request: RequestHeader): Option[User] = {
    request.session.get(Security.username)
      .map(_.trim.toLowerCase)
      .flatMap(UserJdbc.byUserName)
  }

  def onUnAuthenticated(request: RequestHeader) = Results.Redirect(routes.Login.login())

  def onUnAuthorized(user: User) = Results.Redirect(routes.Login.error("You don't have permission to access this page"))

  def withAuth(permission: Permission = rolePermission(User.ADMIN_ROLES ++ Set("jury", "organizer")))
              (f: => User => Request[AnyContent] => Result) = {
    Security.Authenticated(userFromRequest, onUnAuthenticated) { user =>
      Action(request => if (permission(user))
        f(user)(request)
      else
        onUnAuthorized(user)
      )
    }
  }

  def rolePermission(roles: Set[String])(user: User) = user.roles.map(_.role).intersect(roles).nonEmpty

  def rolePermission(roles: Set[String], contestId: Option[Long])(user: User) = user.hasAnyRole(roles, contestId)

  def isRoot(user: User): Boolean = rolePermission(Set(User.ROOT_ROLE))(user)

  def contestPermission(roles: Set[String], contestId: Option[Long])(user: User): Boolean =
    isRoot(user) || user.hasAnyRole(roles, contestId)

  def roundPermission(roles: Set[String], roundId: Long)(user: User): Boolean =
    RoundJdbc.findById(roundId).exists { round =>
      contestPermission(roles, Some(round.contest))(user)
    }
}