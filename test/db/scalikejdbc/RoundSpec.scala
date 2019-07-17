package db.scalikejdbc

import java.time.ZonedDateTime

import org.intracer.wmua.cmd.SetCurrentRound
import org.intracer.wmua.{Round, User}
import org.specs2.mutable.Specification

class RoundSpec extends Specification with InMemDb {

  sequential
  stopOnFail

  val roundDao = RoundJdbc
  val userDao = UserJdbc

  "rounds" should {
    "be empty" in {
      inMemDb {
        val rounds = roundDao.findAll()
        rounds.size === 0
      }
    }

    "insert round" in {
      inMemDb {

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

    def contestUser(contest: Long, role: String, i: Int) =
      User("fullname" + i, "email" + i, None, Set(role), Some("password hash"), Some(contest), Some("en"), Some(now))

    "jurors" in {
      inMemDb {

        val round = Round(None, 1, Some("Round 1"), 10, Set("jury"), 3, Round.ratesById(10), active = true)
        roundDao.create(round)

        val jurors = (1 to 3).map(i => contestUser(10, "jury", i))
        val dbJurors = jurors.map(userDao.create).map(u => u.copy(roles = u.roles + s"USER_ID_${u.getId}"))

        val preJurors = (1 to 3).map(i => contestUser(10, "prejury", i + 10))
        preJurors.foreach(userDao.create)

        val orgCom = contestUser(10, "organizer", 20)
        userDao.create(orgCom)

        val otherContestJurors = (1 to 3).map(i => contestUser(20, "jury", i + 30))
        otherContestJurors.foreach(userDao.create)

        round.jurors === dbJurors
      }
    }

    "set new current round" in {
      inMemDb {
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
