package controllers

import java.util.UUID

import db.scalikejdbc.{Round, User}
import kamon.Kamon
import play.api.mvc._
import kamon.context.Context

/**
  * Base trait for secured controllers
  */
trait Secured {

  type Permission = User => Boolean

  val UserId = Context.key[Option[String]]("userId", None)
  val RequestId = Context.key[Option[String]]("requestId", None)

  /**
    * @param request HTTP request with username set in session
    * @return optional user from database
    */
  def userFromRequest(request: RequestHeader): Option[User] = {
    request.session.get(Security.username)
      .map(_.trim.toLowerCase)
      .flatMap(User.byUserName)
      .map(addUserAndRequestIdToContext)
  }

  def addUserAndRequestIdToContext(user: User): User = {
    user.id.map { id =>
      Kamon.currentContext()
        .withEntry(UserId, Some(id.toString))
        .withEntry(RequestId, Some(UUID.randomUUID().toString))
    }
    user
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

  def rolePermission(roles: Set[String])(user: User) = user.hasAnyRole(roles)

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