package modules

import net.codingwell.scalaguice.ScalaModule
import com.google.inject.{AbstractModule, Provides}
import com.mohiva.play.silhouette.api.services.{AuthenticatorService, IdentityService}
import com.mohiva.play.silhouette.api.{Environment, EventBus}
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import com.mohiva.play.silhouette.impl.providers.oauth2.GoogleProvider
import controllers.DefaultEnv
import org.intracer.wmua.User
import org.scalawiki.MwBot
import play.api.Configuration
import scala.concurrent.ExecutionContext.Implicits.global

class AppModule extends AbstractModule with ScalaModule {

  def configure() = {}

  @Provides
  def bot(configuration: Configuration): MwBot = {
    val host = configuration.get[String]("commons.host")
    MwBot.fromHost(host)
  }

  /**
    * Provides the Silhouette environment.
    *
    * @param userService The user service implementation.
    * @param authenticatorService The authentication service implementation.
    * @param eventBus The event bus instance.
    * @return The Silhouette environment.
    */
  @Provides
  def provideEnvironment(
                          userService: IdentityService[User],
                          authenticatorService: AuthenticatorService[CookieAuthenticator],
                          eventBus: EventBus): Environment[DefaultEnv] = {

    Environment[DefaultEnv](
      userService,
      authenticatorService,
      Seq(),
      eventBus
    )
  }

  /**
    * Provides the social provider registry.
    *
    * @param googleProvider The Google provider implementation.
    * @return The Silhouette environment.
    */
  @Provides
  def provideSocialProviderRegistry(googleProvider: GoogleProvider): SocialProviderRegistry = {
    SocialProviderRegistry(Seq(googleProvider))
  }

}