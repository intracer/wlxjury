// test/graphql2/GraphQL2SpecHelper.scala
package graphql2

import caliban.{CalibanError, GraphQLResponse}
import caliban.ResponseValue
import caliban.ResponseValue._
import caliban.Value._
import db.scalikejdbc.User
import graphql.SubscriptionEventBus
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import zio._

trait GraphQL2SpecHelper {

  private lazy val testSystem: ActorSystem = ActorSystem("caliban-test")
  lazy val testMat: Materializer = Materializer(testSystem)

  lazy val anonCtx: GraphQL2Context =
    GraphQL2Context(None, SubscriptionEventBus.noop, testMat)

  def authedCtx(user: User): GraphQL2Context =
    GraphQL2Context(Some(user), SubscriptionEventBus.noop, testMat)

  def execute(
    query: String,
    ctx:   GraphQL2Context = anonCtx
  ): GraphQLResponse[CalibanError] =
    Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(
        SchemaBuilder.interpreter.execute(query).provideLayer(ZLayer.succeed(ctx))
      ).getOrThrow()
    }

  // ResponseValue navigation helpers
  def field(rv: ResponseValue, name: String): ResponseValue =
    rv.asInstanceOf[ObjectValue].fields.collectFirst { case (k, v) if k == name => v }
      .getOrElse(throw new NoSuchElementException(s"Field '$name' not found in $rv"))

  def dataField(r: GraphQLResponse[CalibanError], name: String): ResponseValue =
    field(r.data, name)

  def list(rv: ResponseValue): List[ResponseValue] =
    rv.asInstanceOf[ListValue].values

  def str(rv: ResponseValue): String =
    rv.asInstanceOf[StringValue].value

  def int(rv: ResponseValue): Int = rv match {
    case IntValue.IntNumber(v)  => v
    case IntValue.LongNumber(v) => v.toInt
    case _                      => throw new Exception(s"Not an int: $rv")
  }

  def bool(rv: ResponseValue): Boolean =
    rv.asInstanceOf[BooleanValue].value

  def errorCode(r: GraphQLResponse[CalibanError]): String = {
    val e = r.errors.head.asInstanceOf[CalibanError.ExecutionError]
    e.extensions.get.fields.collectFirst { case ("code", StringValue(c)) => c }
      .getOrElse("NO_CODE")
  }
}
