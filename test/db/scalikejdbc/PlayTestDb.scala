package db.scalikejdbc

import com.dimafeng.testcontainers.MariaDBContainer
import org.testcontainers.utility.DockerImageName
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.Await
import scala.concurrent.duration._

/** Provides a Play application backed by its own MariaDB container,
 *  started once for the spec class (in beforeAll) and stopped in afterAll.
 *
 *  Use only when tests require Akka actors or other Play services.
 *  `GuiceApplicationBuilder.build()` returns an already-started application;
 *  flyway-play runs migrations automatically on startup via the Play module.
 *  `app.stop()` returns Future[Unit] — Await ensures clean shutdown.
 */
trait PlayTestDb extends TestDb {

  private var _container: MariaDBContainer = _
  protected var app: Application           = _

  def startPlayApp(additionalConfig: Map[String, String] = Map.empty): Unit = {
    _container = MariaDBContainer(
      dockerImageName = DockerImageName.parse("mariadb:10.6.22"),
      dbName         = "wlxjury",
      dbUsername     = "WLXJURY_DB_USER",
      dbPassword     = "WLXJURY_DB_PASSWORD"
    )
    _container.start()
    app = new GuiceApplicationBuilder()
      .configure(Map(
        "db.default.driver"   -> _container.driverClassName,
        "db.default.username" -> _container.username,
        "db.default.password" -> _container.password,
        "db.default.url"      -> _container.jdbcUrl
      ) ++ additionalConfig)
      .build()
    roundDao.usersRef // initialise actor ref required by addUsers
  }

  def stopPlayApp(): Unit = {
    if (app != null) Await.result(app.stop(), 30.seconds)
    if (_container != null) _container.stop()
  }
}
