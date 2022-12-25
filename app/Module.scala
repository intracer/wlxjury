import com.google.inject.AbstractModule
import controllers.KOATUU
import play.api.{Logger, Logging}

class Module extends AbstractModule with Logging {

  override def configure() = {
    logger.info("Application has started")

    KOATUU.load()
  }
}