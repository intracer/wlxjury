package controllers

import db.scalikejdbc.{RoundJdbc, UserJdbc}
import org.intracer.wmua.User
import play.api.mvc._

trait Secured {

  def user(request: RequestHeader): Option[User] = {
    request.session.get(Security.username).map(_.trim.toLowerCase).flatMap(UserJdbc.byUserName)
  }

  def onUnAuthenticated(request: RequestHeader) = Results.Redirect(routes.Login.login())

  def onUnAuthorized(user: User) = Results.Redirect(routes.Login.error("You don't have permission to access this page"))

  def withAuth(permission: Permission = RolePermission(User.ADMIN_ROLES ++ Set("jury", "organizer")))
              (f: => User => Request[AnyContent] => Result) = {
    Security.Authenticated(user, onUnAuthenticated) { user =>
      if (permission.authorized(user))
        Action(request => f(user)(request))
      else
        Action(request => onUnAuthorized(user))
    }
  }

}

trait Permission {
  def authorized(user: User): Boolean
  def isRoot(user: User): Boolean = RolePermission(Set(User.ROOT_ROLE)).authorized(user)
}

case class RolePermission(roles: Set[String]) extends Permission {
  override def authorized(user: User): Boolean =
    roles.intersect(user.roles).nonEmpty
}

case class ContestPermission(roles: Set[String], contestId: Option[Long]) extends Permission {
  override def authorized(user: User): Boolean =
    contestId.fold(isRoot(user)) { id =>
      isRoot(user) || roles.intersect(user.roles).nonEmpty && user.contest.contains(id)
    }
}

case class RoundPermission(roles: Set[String], roundId: Long) extends Permission {
  override def authorized(user: User): Boolean =
    RoundJdbc.find(roundId).exists { round =>
      roles.intersect(user.roles).nonEmpty && user.contest.contains(round.contest)
    }
}