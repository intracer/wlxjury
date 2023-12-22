package db.scalikejdbc

import db.scalikejdbc.RoundSpec.round
import org.intracer.wmua.ContestJury
import org.specs2.mutable.Specification

class RoundSpec extends Specification with TestDb {

  sequential
  stopOnFail

  "rounds" should {
    "be empty" in {
      withDb {
        val rounds = roundDao.findAll()
        rounds.size === 0
      }
    }

    "insert round" in {
      withDb {
        val created = roundDao.create(round)

        val id = created.id
        created === round.copy(id = id)

        val found = roundDao.findById(id.get)
        found === Some(created)

        val all = roundDao.findAll()
        all === Seq(created)
      }
    }

    "available jurors" in {
      withDb {
        implicit val contest = createContests(10).head
        val created = roundDao.create(round)

        val jurors = createUsers(1 to 3).map(u =>
          u.copy(roles = u.roles + s"USER_ID_${u.getId}")
        )
        createUsers("prejury", 11 to 13)
        createUsers("organizer", 20)
        createUsers(31 to 33)(contest.copy(id = Some(20)), implicitly)

        created.availableJurors === jurors
      }
    }

    "set new current round" in {
      withDb {
        val contestDao = ContestJuryJdbc

        val contest =
          contestDao.create(None, "WLE", 2015, "Ukraine", None, None, None)
        val contestId = contest.getId

        val createdAt = now
        val created = roundDao.create(round.copy(createdAt = createdAt, contestId = contestId))

        roundDao.findById(created.getId).map(_.active) === Some(true)
        // TODO fix time issues
        roundDao
          .findById(created.getId)
          .map(_.copy(createdAt = createdAt)) === Some(
          created.copy(createdAt = createdAt)
        )
        roundDao
          .activeRounds(contestId)
          .map(_.copy(createdAt = createdAt)) === Seq(
          created.copy(createdAt = createdAt)
        )
        contestDao.findById(contestId).get.currentRound === None
      }
    }
  }
}

object RoundSpec {
  private val round = Round(
    id = None,
    number = 1,
    name = Some("Round 1"),
    contestId = 10,
    roles = Set("jury"),
    distribution = 3,
    rates = Round.ratesById(10),
    active = true,
    createdAt = TestDb.now
  )
}
