package db.scalikejdbc

import db.scalikejdbc.Contest.autoSession
import org.specs2.mutable.Specification
import scalikejdbc.DBSession

class ContestSpec extends Specification with TestDb {

  sequential

  "fresh database" should {
    "be empty" in {
      withDb {
        val contests = contestDao.findAll()
        contests.size === 0
      }
    }

    "add user to contest" in {
      withDb {
        implicit def session: DBSession = autoSession

        val images = Some("Category:Images from Wiki Loves Earth 2015 in Ukraine")
        val contest = contestDao.create(None, "WLE", 2015, "Ukraine", images, None, None)
        (contest.name, contest.year, contest.country, contest.images) === ("WLE", 2015, "Ukraine", images)

        val dbC = contestDao.findById(contest.getId)
        dbC === Some(contest)

        val contests = contestDao.findAll()
        contests === Seq(contest)

        val user = User("fullname", "email", None, Set("jury"), Some("password hash"), None, Some("en"))
        val dbUser = userDao.create(user)

        dbC.foreach(_.addUser(dbUser, "jury"))
        val users = ContestUser.findAll()
        users.size === 1
        users.head === new ContestUser(dbC.flatMap(_.id).get, dbUser.id.get, "jury")

        contestDao.findById(contest.getId).toSeq.flatMap(_.users) === Seq(dbUser)
      }
    }
  }

//  "create contests" in {
//    withDb {
//      def images(contest: String, year: Int, country: String) =
//        Some(s"Category:Images from $contest $year in $country")
//
//      val contests = Seq(
//        ContestJury(None, "WLE", 2015, "Ukraine", images("WLE", 2015, "Ukraine")),
//        ContestJury(None, "WLM", 2015, "Ukraine", images("WLM", 2015, "Ukraine")),
//        ContestJury(None, "WLM", 2015, "Russia", images("WLM", 2015, "Russia"))
//      )
//      contestDao.batchInsert(contests)
//
//      val fromDb = contestDao.findAll()
//      val withoutIds = fromDb.map(_.copy(id = None))
//      withoutIds === contests
//    }
//  }
}

