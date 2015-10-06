package models

import db.ContestJuryDao
import db.scalikejdbc.ContestJuryJdbc
import org.intracer.wmua.ContestJury
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.test.Helpers._

class ContestSpec extends Specification {

  sequential

  val contestDao: ContestJuryDao = ContestJuryJdbc

  def inMemDbApp[T](block: => T): T = {
    running(FakeApplication(additionalConfiguration = inMemoryDatabase()))(block)
  }

  "fresh database" should {

    "be empty" in {
      inMemDbApp {
        val contests = contestDao.findAll()
        contests.size === 0
      }
    }

    "create contest" in {
      inMemDbApp {
        val images = Some("Category:Images from Wiki Loves Earth 2015 in Ukraine")
        val contest = contestDao.create(None, "WLE", 2015, "Ukraine", images, 0, None)
        (contest.name, contest.year, contest.country, contest.images) ===("WLE", 2015, "Ukraine", images)

        val dbC = contestDao.find(contest.id.get)
        dbC === Some(contest)

        val contests = contestDao.findAll()
        contests === Seq(contest)
      }
    }

    "create contests" in {
      inMemDbApp {
        def images(contest: String, year:Int, country: String) =
          Some(s"Category:Images from $contest $year in $country")

        val contests = Seq(
          ContestJury(None, "WLE", 2015, "Ukraine", images("WLE", 2015, "Ukraine"), 0, None),
          ContestJury(None, "WLM", 2015, "Ukraine", images("WLM", 2015, "Ukraine"), 0, None),
          ContestJury(None, "WLM", 2015, "Russia", images("WLM", 2015, "Russia"), 0, None)
        )
        contestDao.batchInsert(contests)

        val fromDb = contestDao.findAll()
        val withoutIds = fromDb.map(_.copy(id = None))
        withoutIds === contests
      }
    }

  }

}
