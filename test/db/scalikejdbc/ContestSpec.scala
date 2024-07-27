package db.scalikejdbc

import org.intracer.wmua.ContestJury
import org.specs2.mutable.Specification

class ContestSpec extends Specification with TestDb {

  sequential

  "fresh database" should {
    "be empty" in {
      withDb {
        contestDao.findAll() === Nil
      }
    }

    "create contest" in {
      withDb {
        val images =
          Some("Category:Images from Wiki Loves Earth 2015 in Ukraine")

        val contest =
          contestDao.create(
            id = None,
            name = "WLE",
            year = 2015,
            country = "Ukraine",
            images = images,
            categoryId = None,
            currentRound = None
          )

        contest.name === "WLE"
        contest.year === 2015
        contest.country === "Ukraine"
        contest.images === images

        contestDao.findById(contest.getId) === Some(contest)

        contestDao.findAll() === Seq(contest)
      }
    }

    "create contests" in {
      withDb {
        def images(contest: String, year: Int, country: String) =
          Some(s"Category:Images from $contest $year in $country")

        val contests = Seq(
          ContestJury(
            id = None,
            name = "WLE",
            year = 2015,
            country = "Ukraine",
            images = images("WLE", 2015, "Ukraine")
          ),
          ContestJury(
            id = None,
            name = "WLM",
            year = 2015,
            country = "Ukraine",
            images = images("WLM", 2015, "Ukraine")
          ),
          ContestJury(
            id = None,
            name = "WLM",
            year = 2015,
            country = "Poland",
            images = images("WLM", 2015, "Poland")
          )
        )
        contestDao.batchInsert(contests)

        contestDao.findAll().map(_.copy(id = None)) === contests
      }
    }
  }
}
