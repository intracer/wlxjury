package modules

import com.google.inject.{AbstractModule, Provides}
import controllers.KOATUU
import db.RoundRepo
import db.scalikejdbc.Round
import org.scalawiki.MwBot
import play.api.{Configuration, Environment, Logging}

class AppModule(environment: Environment, configuration: Configuration)
    extends AbstractModule
    with Logging {

  override def configure(): Unit = {
    logger.info("Application has started")

    bind(classOf[RoundRepo]).toInstance(Round)

    KOATUU.load()
  }

  @Provides
  def bot: MwBot = {
    val host = configuration.get[String]("commons.host")
    MwBot.fromHost(host)
  }
}
