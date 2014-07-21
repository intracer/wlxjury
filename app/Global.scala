import controllers.LoggingFilter
import play.api.Application
import play.api.mvc.WithFilters
import play.filters.gzip.GzipFilter

object Global extends WithFilters(LoggingFilter, new GzipFilter()) {
  override def onStart(app: Application): Unit = controllers.Global.onStart(app)
}
