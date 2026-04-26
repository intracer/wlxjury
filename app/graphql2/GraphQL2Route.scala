// app/graphql2/GraphQL2Route.scala
package graphql2

import caliban.{CalibanError, GraphQLResponse, InputValue, Value}
import caliban.ResponseValue
import caliban.ResponseValue._
import caliban.Value._
import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
import db.scalikejdbc.User
import graphql.SubscriptionEventBus
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.Materializer
import pdi.jwt.{JwtAlgorithm, JwtJson}
import play.api.Configuration
import play.api.libs.json._
import zio._

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class GraphQL2Route @Inject()(config: Configuration, eventBus: SubscriptionEventBus)(
  implicit ec: ExecutionContext,
  mat: Materializer
) {

  SchemaBuilder.jwtSecret = config.get[String]("graphql.jwt.secret")

  private def resolveUser(authHeader: Option[String], proxyUser: Option[String]): Option[User] =
    proxyUser
      .flatMap(email => User.findByEmail(email).headOption)
      .orElse {
        authHeader.filter(_.startsWith("Bearer ")).flatMap { h =>
          JwtJson
            .decodeJson(h.drop(7), SchemaBuilder.jwtSecret, Seq(JwtAlgorithm.HS256))
            .toOption
            .flatMap(j => (j \ "userId").asOpt[Long])
            .flatMap(User.findById)
        }
      }

  private def jsToInput(v: JsValue): InputValue = v match {
    case JsString(s)  => Value.StringValue(s)
    case JsNumber(n)  => Value.FloatValue.DoubleNumber(n.toDouble)
    case JsBoolean(b) => Value.BooleanValue(b)
    case JsNull       => Value.NullValue
    case JsArray(a)   => InputValue.ListValue(a.map(jsToInput).toList)
    case JsObject(o)  => InputValue.ObjectValue(o.map { case (k, v) => k -> jsToInput(v) }.toMap)
  }

  private def rvToJs(rv: ResponseValue): JsValue = rv match {
    case ObjectValue(fields)            => JsObject(fields.map { case (k, v) => k -> rvToJs(v) }.toMap)
    case ListValue(values)              => JsArray(values.map(rvToJs))
    case StringValue(s)                 => JsString(s)
    case IntValue.IntNumber(n)          => JsNumber(n)
    case IntValue.LongNumber(n)         => JsNumber(n)
    case IntValue.BigIntNumber(n)       => JsNumber(BigDecimal(n))
    case FloatValue.FloatNumber(n)      => JsNumber(BigDecimal(n.toDouble))
    case FloatValue.DoubleNumber(n)     => JsNumber(n)
    case FloatValue.BigDecimalNumber(n) => JsNumber(n)
    case BooleanValue(b)                => JsBoolean(b)
    case NullValue                      => JsNull
    case EnumValue(s)                   => JsString(s)
    case _                              => JsNull
  }

  private def encodeResponse(r: GraphQLResponse[CalibanError]): JsValue = {
    val dataObj = Json.obj("data" -> rvToJs(r.data))
    val errorsArr = r.errors.map {
      case CalibanError.ExecutionError(msg, _, _, _, exts) =>
        val base = Json.obj("message" -> msg)
        exts match {
          case Some(ResponseValue.ObjectValue(fields)) =>
            val ext = JsObject(fields.map { case (k, v) => k -> rvToJs(v) }.toMap)
            base ++ Json.obj("extensions" -> ext)
          case _ => base
        }
      case e => Json.obj("message" -> e.getMessage)
    }
    if (errorsArr.isEmpty) dataObj
    else dataObj ++ Json.obj("errors" -> JsArray(errorsArr))
  }

  val route: Route =
    path("graphql2") {
      post {
        optionalHeaderValueByName("Authorization") { authHeader =>
          optionalHeaderValueByName("X-Authenticated-User") { proxyUser =>
            entity(as[JsValue]) { body =>
              val query  = (body \ "query").as[String]
              val vars   = (body \ "variables").asOpt[JsObject].getOrElse(JsObject.empty)
              val opName = (body \ "operationName").asOpt[String]
              val ctx    = GraphQL2Context(resolveUser(authHeader, proxyUser), eventBus, mat)
              val varMap = vars.fields.map { case (k, v) => k -> jsToInput(v) }.toMap

              val responseFuture = Unsafe.unsafe { implicit u =>
                Runtime.default.unsafe.runToFuture(
                  SchemaBuilder.interpreter
                    .execute(query, opName, varMap)
                    .provideLayer(ZLayer.succeed(ctx))
                )
              }

              complete(responseFuture.map { r =>
                HttpEntity(ContentTypes.`application/json`, encodeResponse(r).toString)
              })
            }
          }
        }
      }
    }
}
