package db.scalikejdbc

import ch.vorburger.mariadb4j.{DB, DBConfigurationBuilder}
import db.scalikejdbc.TestDb._
import org.intracer.wmua.ContestJury
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.running
import scalikejdbc.PlayModule

import java.time.ZonedDateTime

trait TestDb {

  /** Data Access Objects */
  val contestDao: ContestJuryJdbc.type = ContestJuryJdbc
  val imageDao: ImageJdbc.type = ImageJdbc
  val roundDao: Round.type = Round
  val selectionDao: SelectionJdbc.type = SelectionJdbc
  val userDao: User.type = User

  /** Current time accessor */
  def now: ZonedDateTime = TestDb.now

  /** Runs a test block with a temporary embedded database
    *
    * @param block
    *   Function to execute with the application
    * @param additionalConfig
    *   Additional configuration parameters
    * @return
    *   Result of the block execution
    */
  def testDbApp[T](
      block: Application => T
  )(implicit additionalConfig: Map[String, String] = Map.empty): T = {

    val config = DBConfigurationBuilder.newBuilder
    config.setPort(0) // 0 => automatically detect free port

    config.setUnpackingFromClasspath(false)
    config.setLibDir(System.getProperty("java.io.tmpdir") + "/MariaDB4j/no-libs")

    // On MacOS with MariaDB installed via homebrew, you can just set base dir to the output of `brew --prefix`
    val brew = os.proc("/bin/zsh", "-c", "brew --prefix").call().out.text().trim
    config.setBaseDir(brew)

    val db = DB.newEmbeddedDB(config.build())

    try {
      db.start()
      db.createDB(Schema, UserName, Password)
      val port = db.getConfiguration.getPort
      val fakeApp = getApp(additionalConfig, port)
      running(fakeApp)(block(fakeApp))
    } finally {
      db.stop()
    }
  }

  /** Creates a Play application with database configuration
    */
  private def getApp(additionalConfig: Map[String, String], port: Int): Application = {
    val dbConfiguration = Map(
      "db.default.username" -> UserName,
      "db.default.password" -> Password,
      "db.default.url" -> s"jdbc:mysql://localhost:$port/$Schema?autoReconnect=true&autoReconnectForPools=true&useUnicode=true&characterEncoding=UTF-8&useSSL=false"
    )

    new GuiceApplicationBuilder()
      .configure(dbConfiguration ++ additionalConfig)
      .bindings(new PlayModule)
      .build()
  }

  /** Simplified wrapper to run a test block with database
    */
  def withDb[T](block: => T)(implicit additionalConfig: Map[String, String] = Map.empty): T = {
    testDbApp { _ =>
      roundDao.usersRef // init ref TODO fix somehow
      block
    }
  }

  /** Creates test contests with specified IDs
    *
    * @param contestIds
    *   IDs for the contests to create
    * @return
    *   Sequence of created contests
    */
  def createContests(contestIds: Long*): Seq[ContestJury] = contestIds.map { id =>
    val contest = ContestJuryJdbc.create(Some(id), s"contest$id", 2000 + id.toInt, s"country$id")
    ContestJuryJdbc.setImagesSource(id, Some(s"Images from ${contest.name}"))
    contest
  }

  /** Creates a user for a contest with specified role
    */
  def contestUser(i: Long, role: String = "jury")(implicit contest: ContestJury): User =
    User(
      s"fullname$i",
      s"email$i",
      None,
      Set(role),
      Some("password hash"),
      contest.id,
      Some("en"),
      Some(TestDb.now)
    )

  /** Overloaded methods for creating multiple users
    */
  def createUsers(
      userIndexes: Seq[Int]
  )(implicit contest: ContestJury, d: DummyImplicit): Seq[User] =
    createUsers(userIndexes: _*)

  def createUsers(userIndexes: Int*)(implicit contest: ContestJury): Seq[User] =
    createUsers("jury", userIndexes: _*)

  def createUsers(role: String, userIndexes: Seq[Int])(implicit
      contest: ContestJury,
      d: DummyImplicit
  ): Seq[User] = createUsers(role, userIndexes: _*)

  def createUsers(role: String, userIndexes: Int*)(implicit contest: ContestJury): Seq[User] =
    userIndexes
      .map(i => contestUser(i, role))
      .map(userDao.create)
}

object TestDb {

  private val UserName = "WLXJURY_DB_USER"

  private val Password = "WLXJURY_DB_PASSWORD"

  private val Schema = "wlxjury"

  def now: ZonedDateTime = ZonedDateTime.now.withNano(0)

}
