package db.scalikejdbc

import com.dimafeng.testcontainers.MariaDBContainer
import org.flywaydb.core.Flyway
import org.testcontainers.utility.DockerImageName
import scalikejdbc.ConnectionPool

object SharedTestDb {

  /** Lazily starts the container and wires the default ScalikeJDBC pool.
   *  Calling `init()` multiple times is safe — initialization happens once. */
  lazy val initialized: Boolean = {
    val container = MariaDBContainer(
      dockerImageName = DockerImageName.parse("mariadb:10.6.22"),
      dbName         = "wlxjury",
      dbUsername     = "WLXJURY_DB_USER",
      dbPassword     = "WLXJURY_DB_PASSWORD"
    )
    container.start()

    Class.forName(container.driverClassName)
    ConnectionPool.singleton(container.jdbcUrl, container.username, container.password)

    Flyway
      .configure()
      .dataSource(container.jdbcUrl, container.username, container.password)
      .locations("classpath:db/migration/default")
      .load()
      .migrate()

    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      ConnectionPool.closeAll()
      container.stop()
    }))

    true
  }

  /** Call from spec `beforeAll()` to trigger lazy initialisation. */
  def init(): Unit = { val _ = initialized }
}
