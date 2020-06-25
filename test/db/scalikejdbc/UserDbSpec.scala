package db.scalikejdbc

import org.specs2.mutable.Specification

class UserDbSpec extends Specification with TestDb {

  sequential

  "fresh database" should {
    "be empty" in {
      withDb {
        val users = userDao.findAll()
        users.size === 0
      }
    }

    "insert user" in {
      withDb {

        val user = User("fullname", "email", None, Set("jury"), Some("password hash"), Some(10),
          Some("en"), createdAt = Some(now))

        val created = userDao.create(user)

        val id = created.id

        val expected = user.copy(id = id, roles = user.roles ++ Set("USER_ID_" + id.get))
        created === expected

        val found = userDao.findById(id.get)
        found === Some(created)

        val all = userDao.findAll()
        all === Seq(created)
      }
    }
  }
}
