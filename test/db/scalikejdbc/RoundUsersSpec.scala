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

        val jurorActive = userDao.create(User("activeJuror", "email1", None, Set("jury"), contestId = contest.id))
        val jurorNonActive = userDao.create(User("nonActiveJuror", "email2", None, Set("jury"), contestId = contest.id))
        val organiser = userDao.create(User("organiser", "email3", None, Set("jury"), contestId = contest.id))

        round1.users === Nil
        round2.users === Nil

        val roundUsers = Seq(
          RoundUser(round1, jurorActive),
          RoundUser(round1, jurorNonActive).map(_.copy(active = false)),
          RoundUser(round1, organiser)
        ).flatten

        round1.addUsers(roundUsers)

        eventually {
          RoundUser.findAll() === roundUsers

          val roundWithUsers = roundDao.findById(round1.id.get).get
          roundWithUsers.users.map(_.fullname) === Seq(jurorActive, jurorNonActive, organiser).map(_.fullname)
        }
      }
    }
  }

}
