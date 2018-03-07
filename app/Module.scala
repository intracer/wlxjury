import com.codahale.metrics.JmxReporter
import com.google.inject.AbstractModule
import controllers.Global.metrics
import controllers.KOATUU
import play.api.Logger

class Module extends AbstractModule {

  override def configure() = {
    Logger.info("Application has started")

    val reporter = JmxReporter.forRegistry(metrics).build()
    reporter.start()

    KOATUU.load()
  }
}