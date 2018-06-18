package docker

import java.sql.DriverManager

import com.whisk.docker.{DockerCommandExecutor, DockerContainer, DockerContainerState, DockerKit, DockerReadyChecker}

import scala.concurrent.ExecutionContext
import scala.util.Try

trait DockerMysqlService extends DockerKit {
  import scala.concurrent.duration._
  def MySqlAdvertisedPort = 3406
  def MySqlExposedPort = 3406
  val MySqlUser = "ilya"
  val MySqlPassword = "secret"

  val mysqlContainer = DockerContainer("mysql:5.5")
    .withPorts((MySqlAdvertisedPort, Some(MySqlExposedPort)))
    .withEnv(s"MYSQL_DATABASE=wlxjury", s"MYSQL_USER=$MySqlUser", s"MYSQL_PASSWORD=$MySqlPassword",
      "MYSQL_RANDOM_ROOT_PASSWORD=yes")
    .withReadyChecker(
      new MysqlReadyChecker(MySqlUser, MySqlPassword, Some(MySqlExposedPort))
        .looped(15, 1.second)
    )

  abstract override def dockerContainers: List[DockerContainer] =
    mysqlContainer :: super.dockerContainers
}

class MysqlReadyChecker(user: String, password: String, port: Option[Int] = None)
  extends DockerReadyChecker {

  override def apply(container: DockerContainerState)(implicit docker: DockerCommandExecutor,
                                                      ec: ExecutionContext) =
    container
      .getPorts()
      .map(ports =>
        Try {
          Class.forName("com.mysql.jdbc.Driver")
          val url = s"jdbc:mysql://${docker.host}:${port.getOrElse(ports.values.head)}/"
          Option(DriverManager.getConnection(url, user, password)).map(_.close).isDefined
        }.getOrElse(false))
}
