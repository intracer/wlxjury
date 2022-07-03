package db.scalikejdbc

import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

import com.wix.mysql.EmbeddedMysql.anEmbeddedMysql
import com.wix.mysql.config.DownloadConfig.aDownloadConfig
import com.wix.mysql.config.MysqldConfig.aMysqldConfig
import com.wix.mysql.distribution.Version.v5_7_latest
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.running

trait TestDb {

  val contestDao = Contest
  val imageDao = ImageJdbc
  val roundDao = Round
  val selectionDao = SelectionJdbc
  val userDao = User

  def now = ZonedDateTime.now.withNano(0)

  def testDbApp[T](block: Application => T)
                  (implicit additionalConfig: Map[String, String] = Map.empty): T = {
    val downloadConfig = aDownloadConfig()
      .withCacheDir(System.getProperty("user.home") + "/.wixMySQL/downloads")
      .build()
    val config = aMysqldConfig(v5_7_latest)
      .withFreePort()
      .withUser("WLXJURY_DB_USER", "WLXJURY_DB_PASSWORD")
      .withTimeout(60, TimeUnit.SECONDS)
      .build()
    val mysqld = anEmbeddedMysql(config, downloadConfig)
      .addSchema("wlxjury")
      .start()

    val port = mysqld.getConfig.getPort
    val fakeApp = {
      val dbConfiguration = Map(
        "db.default.username" -> "WLXJURY_DB_USER",
        "db.default.password" -> "WLXJURY_DB_PASSWORD",
        "db.default.url" -> s"jdbc:mysql://localhost:$port/wlxjury?autoReconnect=true&autoReconnectForPools=true&useUnicode=true&characterEncoding=UTF-8&useSSL=false"
      )

      new GuiceApplicationBuilder()
        .configure(dbConfiguration ++ additionalConfig)
        .bindings(new scalikejdbc.PlayModule)
        .build
    }

    try {
      running(fakeApp)(block(fakeApp))
    } finally {
      mysqld.stop()
    }
  }

  def withDb[T](block: => T)
               (implicit additionalConfig: Map[String, String] = Map.empty): T = {
    testDbApp { _ =>
      // init ref TODO fix somehow
      roundDao.usersRef
//      contestDao.contestUsers
      block
    }
  }


  def createContests(contestIds: Long*): Seq[Contest] = contestIds.map { id =>
    val contest = Contest.create(Some(id), "contest" + id, 2000 + id.toInt, "country" + id)
    Contest.setImagesSource(id, Some("Images from " + contest.name))
    contest
  }

  def contestUser(i: Long, role: String = "jury")(implicit contest: Contest) =
    User("fullname" + i, "email" + i, None, Set(role), Some("password hash"), contest.id, Some("en"), Some(now))

  def createUsers(userIndexes: Seq[Int])(implicit contest: Contest, d: DummyImplicit): Seq[User] = createUsers(userIndexes: _*)

  def createUsers(userIndexes: Int*)(implicit contest: Contest): Seq[User] = createUsers("jury", userIndexes: _*)

  def createUsers(role: String, userIndexes: Seq[Int])(implicit contest: Contest, d: DummyImplicit): Seq[User] = createUsers(role, userIndexes: _*)

  def createUsers(role: String, userIndexes: Int*)(implicit contest: Contest): Seq[User] = {
    userIndexes
      .map(contestUser(_, role))
      .map(userDao.create)
  }
}