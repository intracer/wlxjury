package db

import org.intracer.wmua.User
import org.joda.time.DateTime

import scala.concurrent.Future

trait UserDao {

  def byUserName(email: String): Future[User]

  def find(id: Long): Future[User]

  def findAll(): List[User]

  def findByContest(contest: Int): List[User]

//  def countByEmail(id: Long, email: String): Long

  def findByEmail(email: String): List[User]

  def countAll(): Long

  def create(
              fullname: String,
              email: String,
              password: String,
              roles: Set[String],
              contest: Int,
              lang: Option[String] = None,
              createdAt: DateTime = DateTime.now
              ): User

  def updateUser(id: Long, fullname: String, email: String, roles: Set[String], lang: Option[String]): Unit

  def updateHash(id: Long, hash:String): Unit

  def destroy(filename: String, email: String): Unit
}
