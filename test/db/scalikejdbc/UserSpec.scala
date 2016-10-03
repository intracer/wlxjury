package db.scalikejdbc

import db.UserDao
import org.intracer.wmua.User
import org.joda.time.DateTime
import org.specs2.mutable.Specification

class UserSpec extends Specification with InMemDb {

  sequential

  val userDao: UserDao = UserJdbc

  "fresh database" should {
    "be empty" in {
      inMemDbApp {
        val users = userDao.findAll()
        users.size === 0
      }
    }

    "insert user" in {
      inMemDbApp {

        val user = User("fullname", "email", None, Set("jury"), Some("password hash"), Some(10),
          Some("en"), createdAt = Some(DateTime.now))

        val created = userDao.create(user)

        val id = created.id

        val expected = user.copy(id = id, roles = user.roles ++ Set("USER_ID_" + id.get))
        created === expected

        val found = userDao.find(id.get)
        found === Some(created)

        val all = userDao.findAll()
        all === Seq(created)
      }
    }
  }

  "parseList" should {

    "parse empty" in {
      User.parseList("") === Seq.empty
    }

    "parse one email" in {
      val list: Seq[User] = User.parseList("123@abc.com")
      val users: Seq[User] = Seq(User(email = "123@abc.com", id = None, contest = None, fullname = ""))
      list === users
    }

    "parse emails" in {
      val emails = Seq("123@abc.com", "234@bcd.com", "345@cde.com")

      User.parseList(emails.mkString("\n")) === emails.map { email =>
        User(id = None, contest = None, fullname = "", email = email)
      }
    }

    "parse wiki accounts" in {
      val accounts = Seq("Ilya", "Antanana", "Ahonc", "Base")

      User.parseList(accounts.mkString("\n")) === accounts.map { account =>
        User(id = None, contest = None, fullname = "", email = "", wikiAccount = Some(account))
      }
    }

    "parse names and emails commas" in {
      val strings = Seq(
        "Name1 Surname1 <email1@server.com>",
        "Name2 Surname2 <email2@server.com>"
      )

      User.parseList(strings.mkString(",")) === Seq(
        User(id = None, contest = None, fullname = "Name1 Surname1", email = "email1@server.com"),
        User(id = None, contest = None, fullname = "Name2 Surname2", email = "email2@server.com")
      )
    }

    "parse names and emails newlines" in {
      val strings = Seq(
        "Name1 Surname1 <email1@server.com>",
        "Name2 Surname2 <email2@server.com>"
      )

      User.parseList(strings.mkString("\n")) === Seq(
        User(id = None, contest = None, fullname = "Name1 Surname1", email = "email1@server.com"),
        User(id = None, contest = None, fullname = "Name2 Surname2", email = "email2@server.com")
      )
    }

    "parse names and emails newlines and commas" in {
      val strings = Seq(
        "Name1 Surname1 <email1@server.com>",
        "Name2 Surname2 <email2@server.com>"
      )

      User.parseList(strings.mkString(",\n")) === Seq(
        User(id = None, contest = None, fullname = "Name1 Surname1", email = "email1@server.com"),
        User(id = None, contest = None, fullname = "Name2 Surname2", email = "email2@server.com")
      )
    }
  }
}
