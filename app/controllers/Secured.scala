package controllers

import play.api.mvc._
import org.intracer.wmua.User

trait Secured {

  def user(request: RequestHeader): Option[User] = {
    request.session.get(Security.username).map(_.trim.toLowerCase).flatMap(User.byUserName)
  }

  def onUnAuthenticated(request: RequestHeader) = Results.Redirect(routes.Application.login())

  def onUnAuthorized() = Results.Redirect(routes.Application.index())

  def withAuth(f: => User => Request[AnyContent] => Result, roles: Set[String] = Set(User.ADMIN_ROLE, "jury", "organizer")) = {
    Security.Authenticated(user, onUnAuthenticated) { user =>
      if (!roles.intersect(user.roles).isEmpty)
        Action(request => f(user)(request))
      else
        Action(request => onUnAuthorized())
    }
  }

  /**
   * This method shows how you could wrap the withAuth method to also fetch your user
   * You will need to implement UserDAO.findOneByUsername
   */
//  def withUser(f: User => Request[AnyContent] => Result) = withAuth { username => implicit request =>
//    User.login(username, "123").map { user =>
//      f(user)(request)
//    }.getOrElse(onUnAuthenticated(request))
//  }
}