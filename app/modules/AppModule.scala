package modules

import com.google.inject.{AbstractModule, Provides}
import org.scalawiki.MwBot
import play.api.{Configuration, Environment}

class AppModule(environment: Environment, configuration: Configuration) extends AbstractModule {

  override def configure(): Unit = {}

  @Provides
  def bot: MwBot = {
    val host = configuration.get[String]("commons.host")
    MwBot.fromHost(host)
  }
}