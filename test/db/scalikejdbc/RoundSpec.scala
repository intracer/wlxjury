package db.scalikejdbc

import db.{UserDao, RoundDao}
import org.intracer.wmua.{User, Round}
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.test.Helpers._

class RoundSpec extends Specification {

  sequential

  val roundDao: RoundDao = RoundJdbc
  val userDao: UserDao = UserJdbc

  def inMemDbApp[T](block: => T): T = {
    running(FakeApplication(additionalConfiguration = inMemoryDatabase()))(block)
  }

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

        val found = roundDao.find(id.get)
        found === Some(created)

        val all = roundDao.findAll()
        all === Seq(created)
      }
    }

    def contestUser(contest: Long, role: String) =
      User("fullname", "email", None, Set(role), Some("password hash"), Some(contest), Some("en"))

    "jurors" in {
      inMemDbApp {

        val round = Round(None, 1, Some("Round 1"), 10, Set("jury"), 3, Round.ratesById(10), active = true)
        roundDao.create(round)

        val jurors = (1 to 3).map(i => contestUser(10, "jury"))
        val dbJurors = jurors.map(userDao.create).map(u => u.copy(roles = u.roles + s"USER_ID_${u.id.get}"))

        val preJurors = (1 to 3).map(i => contestUser(10, "prejury"))
        preJurors.foreach(userDao.create)

        val orgCom = contestUser(10, "organizer")
        userDao.create(orgCom)

        val otherContestJurors = (1 to 3).map(i => contestUser(20, "jury"))
        otherContestJurors.foreach(userDao.create)

        round.jurors === dbJurors
      }
    }
  }
}
