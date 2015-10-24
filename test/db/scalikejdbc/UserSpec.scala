package db.scalikejdbc

import db.UserDao
import org.intracer.wmua.User
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.test.Helpers._

class UserSpec extends Specification {

  sequential

  val userDao: UserDao = UserJdbc

  def inMemDbApp[T](block: => T): T = {
    running(FakeApplication(additionalConfiguration = inMemoryDatabase()))(block)
  }

  "fresh database" should {

    "be empty" in {
      inMemDbApp {
        val users = userDao.findAll()
        users.size === 0
      }
    }

    "insert user" in {
      inMemDbApp {

        val user = User("fullname", "email", None, Set("jury", "USER_ID_1"), Some("password hash"), 10, Some("en"))

        val created = userDao.create(user)

        val id = created.id

        created === user.copy(id = id)

        val found = userDao.find(id.get)
        found === Some(created)

        val all = userDao.findAll()
        all === Seq(created)
      }
    }
  }

}
