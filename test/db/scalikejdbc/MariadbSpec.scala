package db.scalikejdbc

import com.dimafeng.testcontainers.{ForAllTestContainer, MariaDBContainer, MySQLContainer}
import org.scalatest.flatspec.AnyFlatSpec
import org.testcontainers.utility.DockerImageName
import play.api.db.Databases

import java.sql.DriverManager
import java.util.Properties

class MariadbSpec extends AnyFlatSpec with ForAllTestContainer {

  override val container: MariaDBContainer =
    MariaDBContainer(dockerImageName = DockerImageName.parse("mariadb:10.3.39"))

  "Mariadb container" should "be started" in {
    Class.forName(container.driverClassName)
    val properties = new Properties();
    properties.setProperty("user", container.username);
    properties.setProperty("password", container.password)
    properties.setProperty("useSSL", "false")
    val connection = DriverManager.getConnection(container.jdbcUrl, properties)

    val prepareStatement = connection.prepareStatement("select 1")
    try {
      val resultSet = prepareStatement.executeQuery()
      resultSet.next()
      assert(1 == resultSet.getInt(1))
      resultSet.close()
    } finally {
      prepareStatement.close()
    }

    val database = Databases(
      driver = "com.mysql.cj.jdbc.Driver",
      url = container.jdbcUrl,
      name = "default",
      config = Map(
        "username" -> container.username,
        "password" -> container.password
      )
    )


    connection.close()
  }
}
