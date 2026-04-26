// app/graphql2/GraphQL2Context.scala
package graphql2

import caliban.CalibanError
import caliban.ResponseValue.ObjectValue
import caliban.Value.StringValue
import db.scalikejdbc.User
import graphql.SubscriptionEventBus
import org.apache.pekko.stream.Materializer
import zio._

case class GraphQL2Context(
  currentUser: Option[User],
  eventBus:    SubscriptionEventBus,
  mat:         Materializer
) {

  def requireAuth: IO[CalibanError, User] =
    ZIO.fromOption(currentUser).orElseFail(
      CalibanError.ExecutionError("Not authenticated",
        extensions = Some(ObjectValue(List("code" -> StringValue("UNAUTHENTICATED")))))
    )

  def requireRole(role: String): IO[CalibanError, User] =
    requireAuth.flatMap { u =>
      if (u.hasRole(role) || u.hasRole("root")) ZIO.succeed(u)
      else ZIO.fail(CalibanError.ExecutionError(s"Requires role: $role",
        extensions = Some(ObjectValue(List("code" -> StringValue("FORBIDDEN"))))))
    }
}

object GraphQL2Context {
  def dbError(e: Throwable): CalibanError.ExecutionError =
    CalibanError.ExecutionError(e.getMessage,
      extensions = Some(ObjectValue(List("code" -> StringValue("INTERNAL_ERROR")))))

  def notFound(msg: String): CalibanError.ExecutionError =
    CalibanError.ExecutionError(msg,
      extensions = Some(ObjectValue(List("code" -> StringValue("NOT_FOUND")))))
}
