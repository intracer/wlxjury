package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import org.intracer.wmua.User

trait Secured {

  def username(request: RequestHeader) = request.session.get(Security.username)

  def onUnauthorized(request: RequestHeader) = Results.Redirect(routes.Application.index())

  def withAuth(f: => String => Request[AnyContent] => Result) = {
    Security.Authenticated(username, onUnauthorized) { user =>
      Action(request => f(user)(request))
    }
  }

  /**
   * This method shows how you could wrap the withAuth method to also fetch your user
   * You will need to implement UserDAO.findOneByUsername
   */
  def withUser(f: User => Request[AnyContent] => Result) = withAuth { username => implicit request =>
    User.login(username, "123").map { user =>
      f(user)(request)
    }.getOrElse(onUnauthorized(request))
  }
}