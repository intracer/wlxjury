package db.slick

import db.UserDao
import org.intracer.wmua.User
import org.joda.time.DateTime
import play.api.Play
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfig}
import slick.driver.JdbcProfile

import scala.concurrent.Future

class UserDaoSlick extends HasDatabaseConfig[JdbcProfile] with UserDao {
  protected val dbConfig = DatabaseConfigProvider.get[JdbcProfile](Play.current)

  import driver.api._

  private val Users = TableQuery[UserTable]

  class UserTable(tag: Tag) extends Table[User](tag, "users") {

    def id = column[Long]("id", O.PrimaryKey)

    def fullname = column[String]("fullname")

    def email = column[String]("email")

    def roles = column[String]("roles")

    def password = column[Option[String]]("password")

    def contest = column[Int]("contest")

    def lang = column[Option[String]]("lang")

    def createdAt = column[DateTime]("created_at")

    def deletedAt = column[Option[DateTime]]("deleted_at")

    def * = (fullname, email, id, roles, password, contest, lang, createdAt, deletedAt) <>(User.tupled, User.unapply)

  }

  override def byUserName(email: String): Future[User] =
    db.run(Users.filter(_.email === email).result.head)

  override def findByEmail(email: String): Future[User] = byUserName(email)

  override def find(id: Long): Future[User] =
    db.run(Users.filter(_.id === id).result.head)

  override def findByContest(contest: Int): Future[Seq[User]] =
    db.run(Users.filter(_.contest === contest).result)

  override def findAll(): Future[Seq[User]] =
    db.run(Users.result)

  override def countAll(): Future[Int] =
    db.run(Users.length.result)

  override def destroy(filename: String, email: String): Unit =
    ???

  override def updateHash(id: Long, hash: String): Future[Int] =
    db.run(Users.filter(_.id === id).map(_.password).update(Option(hash)))

  override def updateUser(id: Long, fullname: String, email: String, roles: Set[String], lang: Option[String]): Future[Int] =
    db.run(Users.filter(_.id === id).map { u =>
      (u.fullname, u.email, u.roles, u.lang)
    }.update(
        (fullname, email, roles.mkString(","), lang)
      )
    )

  override def create(
                       fullname: String,
                       email: String,
                       password: String,
                       roles: Set[String],
                       contest: Int,
                       lang: Option[String],
                       createdAt: DateTime): User =
    db.run(Users += new User(fullname, email, None, roles, Option(password), contest, lang, Seq.empty, createdAt))

//  override def countByEmail(id: Long, email: String): Long = ???
}
