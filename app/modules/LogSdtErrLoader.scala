package modules

import play.api.ApplicationLoader
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceApplicationLoader}
import uk.org.lidalia.sysoutslf4j.context.SysOutOverSLF4J

/**
  * For http://stackoverflow.com/questions/38736689/how-to-log-play-framework-startup-errors
  */
class LogSdtErrLoader extends GuiceApplicationLoader {

  override final def builder(context: ApplicationLoader.Context): GuiceApplicationBuilder = {
    SysOutOverSLF4J.sendSystemOutAndErrToSLF4J()
    super.builder(context)
  }
}


