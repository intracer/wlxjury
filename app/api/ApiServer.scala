package api

import graphql.GraphQLRoute
import graphql2.GraphQL2Route
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Directives.concat
import org.apache.pekko.http.scaladsl.server.Route
import play.api.inject.ApplicationLifecycle

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ApiServer @Inject()(
  api:           Api,
  graphQLRoute:  GraphQLRoute,
  graphQL2Route: GraphQL2Route,
  actorSystem:   ActorSystem,
  lifecycle:     ApplicationLifecycle
)(implicit ec: ExecutionContext) {

  private val route = concat(api.routes, api.swaggerRoute, graphQLRoute.route, graphQL2Route.route)
  private val bindingFuture =
    Http(actorSystem).newServerAt("0.0.0.0", 9001).bind(Route.toFunction(route)(actorSystem))

  lifecycle.addStopHook { () =>
    bindingFuture.flatMap(_.unbind())
  }
}
