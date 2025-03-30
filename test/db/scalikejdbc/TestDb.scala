package db.scalikejdbc

import ch.vorburger.mariadb4j.DB
import org.intracer.wmua.ContestJury
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.running
import scalikejdbc.PlayModule

import java.time.ZonedDateTime

trait TestDb {

  val contestDao = ContestJuryJdbc
  val imageDao = ImageJdbc
  val roundDao = Round
  val selectionDao = SelectionJdbc
  val userDao = User

  def now: ZonedDateTime = TestDb.now

  def testDbApp[T](
      block: Application => T
  )(implicit additionalConfig: Map[String, String] = Map.empty): T = {

    val db = DB.newEmbeddedDB(0)

    try {
      db.start()
      db.createDB("wlxjury", "WLXJURY_DB_USER", "WLXJURY_DB_PASSWORD")
      val port = db.getConfiguration.getPort
      val fakeApp = getApp(additionalConfig, port)
      running(fakeApp)(block(fakeApp))
    } finally {
      db.stop()
    }
  }

  private def getApp[T](additionalConfig: Map[String, String], port: Int) = {

    val dbConfiguration = Map(
      "db.default.username" -> "WLXJURY_DB_USER",
      "db.default.password" -> "WLXJURY_DB_PASSWORD",
      "db.default.url" -> s"jdbc:mysql://localhost:$port/wlxjury?autoReconnect=true&autoReconnectForPools=true&useUnicode=true&characterEncoding=UTF-8&useSSL=false"
    )

    new GuiceApplicationBuilder()
      .configure(dbConfiguration ++ additionalConfig)
      .bindings(new PlayModule)
      .build()

  }

  def withDb[T](block: => T)(implicit additionalConfig: Map[String, String] = Map.empty): T = {
    testDbApp { _ =>
      roundDao.usersRef // init ref TODO fix somehow
      block
    }
  }

  def createContests(contestIds: Long*): Seq[ContestJury] = contestIds.map { id =>
    val contest = ContestJuryJdbc.create(Some(id), "contest" + id, 2000 + id.toInt, "country" + id)
    ContestJuryJdbc.setImagesSource(id, Some("Images from " + contest.name))
    contest
  }

  def contestUser(i: Long, role: String = "jury")(implicit contest: ContestJury) =
    User(
      "fullname" + i,
      "email" + i,
      None,
      Set(role),
      Some("password hash"),
      contest.id,
      Some("en"),
      Some(TestDb.now)
    )

  def createUsers(
      userIndexes: Seq[Int]
  )(implicit contest: ContestJury, d: DummyImplicit): Seq[User] = createUsers(userIndexes: _*)

  def createUsers(userIndexes: Int*)(implicit contest: ContestJury): Seq[User] =
    createUsers("jury", userIndexes: _*)

  def createUsers(role: String, userIndexes: Seq[Int])(implicit
      contest: ContestJury,
      d: DummyImplicit
  ): Seq[User] = createUsers(role, userIndexes: _*)

  def createUsers(role: String, userIndexes: Int*)(implicit contest: ContestJury): Seq[User] = {
    userIndexes
      .map(contestUser(_, role))
      .map(userDao.create)
  }
}

object TestDb {

  def now: ZonedDateTime = ZonedDateTime.now.withNano(0)

}
