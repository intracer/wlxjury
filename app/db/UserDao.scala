package db

import org.intracer.wmua.User
import org.joda.time.DateTime

trait UserDao {

  def byUserName(email: String): Option[User]

  def find(id: Long): Option[User]

  def findAll(): Seq[User]

  def findByContest(contest: Long): Seq[User]

  def countByEmail(id: Long, email: String): Long

  def findByEmail(email: String): Seq[User]

  def countAll(): Long

  def create(
              fullname: String,
              email: String,
              password: String,
              roles: Set[String],
              contest: Long,
              lang: Option[String] = None,
              createdAt: DateTime = DateTime.now
              ): User

  def create(user: User): User

  def updateUser(id: Long, fullname: String, email: String, roles: Set[String], lang: Option[String]): Unit

  def updateHash(id: Long, hash:String): Unit

  def destroy(filename: String, email: String): Unit
}
