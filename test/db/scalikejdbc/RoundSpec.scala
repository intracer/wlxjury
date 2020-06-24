package db.scalikejdbc

import org.intracer.wmua.cmd.SetCurrentRound
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
        val round = Round(None, 1, Some("Round 1"), 10, Set("jury"), 3, Round.ratesById(10), active = true, createdAt = now)
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
        val round = Round(None, 1, Some("Round 1"), 10, Set("jury"), 3, Round.ratesById(10), active = true)
        roundDao.create(round)

        val jurors = createUsers(1 to 3).map(u => u.copy(roles = u.roles + s"USER_ID_${u.getId}"))
        val preJurors = createUsers("prejury", 11 to 13)
        val orgCom = createUsers("organizer", 20)
        val otherContestJurors = createUsers(31 to 33)(contest.copy(id = Some(20)), implicitly)

        round.availableJurors === jurors
      }
    }

    "set new current round" in {
      withDb {
        val contestDao = ContestJuryJdbc

        val contest = contestDao.create(None, "WLE", 2015, "Ukraine", None, None, None)
        val contestId = contest.getId

        val createdAt = now
        val round = roundDao.create(Round(None, 1, Some("Round 1"), contest.getId, Set("jury"), 0, Round.ratesById(10), createdAt = createdAt))

        roundDao.findById(round.getId).map(_.active) === Some(false)
        roundDao.activeRounds(contestId) === Seq.empty
        contestDao.findById(contestId).get.currentRound === None

        val activeRound = round.copy(active = true)

        SetCurrentRound(contestId, None, activeRound).apply()

        // TODO fix time issues
        roundDao.findById(round.getId).map(_.copy(createdAt = createdAt)) === Some(activeRound.copy(createdAt = createdAt))
        roundDao.activeRounds(contestId).map(_.copy(createdAt = createdAt)) === Seq(activeRound.copy(createdAt = createdAt))
        contestDao.findById(contestId).get.currentRound === None
      }
    }
  }
}
