import play.api.http.DefaultHttpErrorHandler
import play.api.i18n._
import play.api.mvc.Results._
import play.api.mvc._
import play.api.{Configuration, Environment, Logging}

import javax.inject.Inject
import scala.concurrent._

class ErrorHandler @Inject() (
    environment: Environment,
    configuration: Configuration,
    val langs: Langs,
    val messagesApi: MessagesApi
) extends DefaultHttpErrorHandler(environment, configuration, sourceMapper = None, router = None)
    with I18nSupport
    with Logging {

  override def onClientError(
      request: RequestHeader,
      statusCode: Int,
      message: String
  ): Future[Result] = {
    implicit val impReq: RequestHeader = request
    val fullMessage = "An error occurred: " + statusCode + (if (message.nonEmpty)
                                                              ", " + message
                                                            else "")
    logger.error(fullMessage)
    Future.successful(
      Status(statusCode)(views.html.error(fullMessage))
    )
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    implicit val impReq: RequestHeader = request
    logger
      .error("A server error occurred: " + exception.getMessage, exception)
    Future.successful(
      InternalServerError(views.html.error("A server error occurred: " + exception.getMessage))
    )
  }
}
