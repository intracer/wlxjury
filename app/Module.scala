import com.google.inject.AbstractModule
import controllers.KOATUU
import play.api.Logger

class Module extends AbstractModule {

  override def configure() = {
    Logger.info("Application has started")

    KOATUU.load()
  }
}