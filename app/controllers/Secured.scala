package controllers

import org.intracer.wmua.User
import play.api.mvc._

trait Secured {

  def user(request: RequestHeader): Option[User] = {
    request.session.get(Security.username).map(_.trim.toLowerCase).flatMap(User.byUserName)
  }

  def onUnAuthenticated(request: RequestHeader) = Results.Redirect(routes.Login.login())

  def onUnAuthorized(user: User) = Results.Redirect(routes.Login.error("You don't have permission to access this page"))

  def withAuth(f: => User => Request[AnyContent] => Result, roles: Set[String] = Set(User.ADMIN_ROLE, "jury", "organizer")) = {
    Security.Authenticated(user, onUnAuthenticated) { user =>
      if (roles.intersect(user.roles).nonEmpty)
        Action(request => f(user)(request))
      else
        Action(request => onUnAuthorized(user))
    }
  }

  def withAuthBP[A](bodyParser: BodyParser[A])(f: => User => Request[A] => Result, roles: Set[String] = Set(User.ADMIN_ROLE, "jury", "organizer")) = {
    Security.Authenticated(user, onUnAuthenticated) { user =>
      if (roles.intersect(user.roles).nonEmpty)
        Action(bodyParser)(request => f(user)(request))
      else
        Action(bodyParser)(request => onUnAuthorized(user))
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