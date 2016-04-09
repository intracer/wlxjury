package db.slick

import db.UserDao
import org.intracer.wmua.User
import org.joda.time.DateTime
import play.api.Play
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfig}
import slick.driver.JdbcProfile
import spray.util.pimpFuture

class UserDaoSlick extends HasDatabaseConfig[JdbcProfile] with UserDao {
  protected val dbConfig = DatabaseConfigProvider.get[JdbcProfile](Play.current)

  import driver.api._

  private val Users = TableQuery[UserTable]

  val autoInc = Users returning Users.map(_.id)

  class UserTable(tag: Tag) extends Table[User](tag, "users") {

    def id = column[Option[Long]]("id", O.PrimaryKey)

    def fullname = column[String]("fullname")

    def email = column[String]("email")

    def roles = column[String]("roles")

    def password = column[Option[String]]("password")

    def contest = column[Option[Long]]("contest")

    def lang = column[Option[String]]("lang")

    //    def createdAt = column[DateTime]("created_at")
    //
    //    def deletedAt = column[Option[DateTime]]("deleted_at")

    def * = (fullname, email, id, roles, password, contest, lang /*, createdAt, deletedAt*/ ) <> (UserTable.fromDb, UserTable.toDb)

  }

  override def byUserName(email: String): Option[User] =
    db.run(Users.filter(_.email === email).result.headOption).await

  override def findByEmail(email: String): Seq[User] = byUserName(email).toSeq

  override def find(id: Long): Option[User] =
    db.run(Users.filter(_.id === id).result.headOption).await

  override def findByContest(contest: Long): Seq[User] =
    db.run(Users.filter(_.contest === contest.toLong).result).await

  override def findAll(): Seq[User] =
    db.run(Users.result).await

  override def countAll(): Long =
    db.run(Users.length.result).await

  override def destroy(filename: String, email: String): Unit =
    ???

  override def updateHash(id: Long, hash: String): Unit =
    db.run(Users.filter(_.id === id).map(_.password).update(Option(hash))).await

  override def updateUser(id: Long, fullname: String, email: String, roles: Set[String], lang: Option[String]): Unit =
    db.run(Users.filter(_.id === id).map { u =>
      (u.fullname, u.email, u.roles, u.lang)
    }.update(
        (fullname, email, roles.mkString(","), lang)
      )
    ).await

  override def create(
                       fullname: String,
                       email: String,
                       password: String,
                       roles: Set[String],
                       contest: Option[Long],
                       lang: Option[String],
                       createdAt: DateTime) = {
    val id = db.run(
      autoInc += new User(fullname, email, None, roles, Option(password), contest, lang)
    ).await

    new User(fullname, email, id, roles, Option(password), contest, lang)
  }

  override def create(user: User): User = ???

  override def countByEmail(id: Long, email: String): Long = ???
}


object UserTable {

  def fromDb(t: (
    String, // fullname
      String, // email
      Option[Long], // id
      String, // roles
      Option[String], // password
      Option[Long], // contest
      Option[String] // lang
    ))
  =
    new User(
      fullname = t._1,
      email = t._2,
      id = t._3,
      roles = t._4.split(",").toSet,
      password = t._5,
      contest = t._6,
      lang = t._7
      //      createdAt = t._11
    )

  def toDb(u: User) =
    Some(
      u.fullname,
      u.email,
      u.id,
      u.roles.mkString(","),
      u.password,
      u.contest,
      u.lang
    )
}