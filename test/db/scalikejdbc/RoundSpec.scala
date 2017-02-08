package db.scalikejdbc

import db.UserDao
import org.intracer.wmua.{Round, User}
import org.specs2.mutable.Specification

class RoundSpec extends Specification with InMemDb {

  sequential

  val roundDao = RoundJdbc
  val userDao: UserDao = UserJdbc

  "fresh database" should {
    "be empty" in {
      inMemDbApp {
        val rounds = roundDao.findAll()
        rounds.size === 0
      }
    }

    "insert round" in {
      inMemDbApp {

        val round = Round(None, 1, Some("Round 1"), 10, Set("jury"), 3, Round.ratesById(10), active = true)

        val created = roundDao.create(round)

        val id = created.id

        created === round.copy(id = id)

        val found = roundDao.findById(id.get)
        found === Some(created)

        val all = roundDao.findAll()
        all === Seq(created)
      }
    }

    def contestUser(contest: Long, role: String, i: Int) =
      User("fullname" + i, "email" + i, None, Set(role), Some("password hash"), Some(contest), Some("en"))

    "jurors" in {
      inMemDbApp {

        val round = Round(None, 1, Some("Round 1"), 10, Set("jury"), 3, Round.ratesById(10), active = true)
        roundDao.create(round)

        val jurors = (1 to 3).map(i => contestUser(10, "jury", i))
        val dbJurors = jurors.map(userDao.create).map(u => u.copy(roles = u.roles + s"USER_ID_${u.id.get}"))

        val preJurors = (1 to 3).map(i => contestUser(10, "prejury", i + 10))
        preJurors.foreach(userDao.create)

        val orgCom = contestUser(10, "organizer", 20)
        userDao.create(orgCom)

        val otherContestJurors = (1 to 3).map(i => contestUser(20, "jury", i + 30))
        otherContestJurors.foreach(userDao.create)

        round.jurors === dbJurors
      }
    }
  }
}
