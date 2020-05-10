package db.scalikejdbc

import org.specs2.mutable.Specification

class RoundUsersSpec extends Specification with TestDb {
  sequential
  stopOnFail

  "round users" should {
    "assign round to jurors" in {
      withDb {
        implicit val contest = contestDao.create(None, "WLE", 2015, "Ukraine")

        val round1 = roundDao.create(Round(None, 1, Some("Round 1"), 10))
        val round2 = roundDao.create(Round(None, 2, Some("Round 2"), 10))

        ok
      }
    }
  }

}
