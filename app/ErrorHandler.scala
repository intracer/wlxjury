import javax.inject.Inject

import play.api.{Logger, mvc}
import play.api.http.HttpErrorHandler
import play.api.mvc._
import play.api.mvc.Results._

import scala.concurrent._
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api.i18n._

class ErrorHandler @Inject()(val langs: Langs, val messagesApi: MessagesApi) extends HttpErrorHandler with I18nSupport {

  def onClientError(request: RequestHeader, statusCode: Int, message: String) = {
    implicit val lang = request.messages.lang
    val fullMessage = "An error occurred: " + statusCode + (if (message.nonEmpty) ", " + message else "")
    Logger.logger.error(fullMessage)
    Future.successful(
      Status(statusCode)(views.html.error(fullMessage))
    )
  }

  def onServerError(request: RequestHeader, exception: Throwable) = {
    implicit val lang = request.messages.lang
    Logger.logger.error("A server error occurred: " + exception.getMessage, exception)
    Future.successful(
      InternalServerError(views.html.error("A server error occurred: " + exception.getMessage))
    )
  }
}