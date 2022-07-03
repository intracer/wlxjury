package db.scalikejdbc

import org.specs2.mutable.Specification

class ContestSpec extends Specification with TestDb {

  sequential

  "fresh database" should {
    "be empty" in {
      withDb {
        val contests = contestDao.findAll()
        contests.size === 0
      }
    }

    "create contest" in {
      withDb {
        val images = Some("Category:Images from Wiki Loves Earth 2015 in Ukraine")
        val contest = contestDao.create(None, "WLE", 2015, "Ukraine", images, None, None)
        (contest.name, contest.year, contest.country, contest.images) ===("WLE", 2015, "Ukraine", images)

        val dbC = contestDao.findById(contest.getId)
        dbC === Some(contest)

        val contests = contestDao.findAll()
        contests === Seq(contest)
      }
    }

    "create contests" in {
      withDb {
        def images(contest: String, year: Int, country: String) =
          Some(s"Category:Images from $contest $year in $country")

        val contests = Seq(
          ContestJury(None, "WLE", 2015, "Ukraine", images("WLE", 2015, "Ukraine")),
          ContestJury(None, "WLM", 2015, "Ukraine", images("WLM", 2015, "Ukraine")),
          ContestJury(None, "WLM", 2015, "Russia", images("WLM", 2015, "Russia"))
        )
        contestDao.batchInsert(contests)

        val fromDb = contestDao.findAll()
        val withoutIds = fromDb.map(_.copy(id = None))
        withoutIds === contests
      }
    }
  }
}
