package db.scalikejdbc

import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.running

trait InMemDb {

  def fakeApp = {
    val additionalConfiguration = Map(
      "db.default.driver" -> "org.h2.Driver",
      "db.default.url" -> "jdbc:h2:mem:default",
      "db.default.user" -> "WLXJURY_DB_USER",
      "db.default.password" -> "WLXJURY_DB_PASSWORD"
    )

    new GuiceApplicationBuilder()
      .configure(additionalConfiguration)
      .bindings(new scalikejdbc.PlayModule)
      .build
  }

  def inMemDbApp[T](block: => T): T = {
    running(fakeApp) {
      block
    }
  }
}
