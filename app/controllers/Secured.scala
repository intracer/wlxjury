package controllers

import play.api.mvc._
import org.intracer.wmua.User

trait Secured {

  def username(request: RequestHeader) = request.session.get(Security.username).map(_.trim.toLowerCase)

  def onUnAuthenticated(request: RequestHeader) = Results.Redirect(routes.Application.index())

  def onUnAuthorized() = Results.Redirect(routes.Application.index())

  def withAuth(f: => String => Request[AnyContent] => Result, roles: Set[String] = Set(User.ADMIN_ROLE, "jury", "organizer")) = {
    Security.Authenticated(username, onUnAuthenticated) { user =>
      if (!roles.intersect(User.byUserName(user).roles).isEmpty)
        Action(request => f(user)(request))
      else
        Action(request => onUnAuthorized())
    }
  }

  /**
   * This method shows how you could wrap the withAuth method to also fetch your user
   * You will need to implement UserDAO.findOneByUsername
   */
  def withUser(f: User => Request[AnyContent] => Result) = withAuth { username => implicit request =>
    User.login(username, "123").map { user =>
      f(user)(request)
    }.getOrElse(onUnAuthenticated(request))
  }
}