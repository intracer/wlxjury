package controllers

import db.scalikejdbc.UserJdbc
import org.intracer.wmua.User
import play.api.mvc._

trait Secured {

  def user(request: RequestHeader): Option[User] = {
    request.session.get(Security.username).map(_.trim.toLowerCase).flatMap(UserJdbc.byUserName)
  }

  def onUnAuthenticated(request: RequestHeader) = Results.Redirect(routes.Login.login())

  def onUnAuthorized(user: User) = Results.Redirect(routes.Login.error("You don't have permission to access this page"))

  def withAuth(f: => User => Request[AnyContent] => Result,
               roles: Set[String] = User.ADMIN_ROLES ++ Set("jury", "organizer")) = {
    Security.Authenticated(user, onUnAuthenticated) { user =>
      if (roles.intersect(user.roles).nonEmpty)
        Action(request => f(user)(request))
      else
        Action(request => onUnAuthorized(user))
    }
  }

}