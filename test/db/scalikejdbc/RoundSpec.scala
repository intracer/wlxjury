package db.scalikejdbc

import db.RoundDao
import org.intracer.wmua.Round
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.test.Helpers._

class RoundSpec extends Specification {

  sequential

  val roundDao: RoundDao = RoundJdbc

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
  }

}
