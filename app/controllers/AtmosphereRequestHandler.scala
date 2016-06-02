package controllers

import javax.inject.Inject

import org.atmosphere.play.{AtmosphereCoordinator, AtmosphereHttpRequestHandler}
import play.api.http._
import play.api.mvc.RequestHeader
import play.api.routing.Router

class AtmosphereRequestHandler @Inject()(atm: AtmosphereHttpRequestHandler, errorHandler: HttpErrorHandler,
                                         configuration: HttpConfiguration, filters: HttpFilters,
                                         router: Router
                                        ) extends DefaultHttpRequestHandler(
  router, errorHandler, configuration, filters
) {
  AtmosphereCoordinator.instance.discover(classOf[ProgressController]).ready

  override def routeRequest(request: RequestHeader) = {
    request.path match {
      case "/progress" => atm.dispatch(request)
      case _ => super.routeRequest(request)
    }
  }
}
