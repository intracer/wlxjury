package db.scalikejdbc

import db.scalikejdbc.RoundImagesSpec.round
import org.intracer.wmua.cmd.DistributeImages
import org.specs2.mutable.Specification

class RoundImagesSpec extends Specification with TestDb {

  sequential
  stopOnFail

  "rounds" should {

    "DistributeImages in round" in {
      withDb {
        val created = roundDao.create(round)
        new DistributeImages(ImageJdbc).imagesByRound(created, None) === Nil
      }
    }
  }
}

object RoundImagesSpec {
  private val round = Round(
    id = None,
    number = 1,
    name = Some("Round 1"),
    contestId = 10,
    roles = Set("jury"),
    distribution = 3,
    rates = Round.ratesById(10),
    active = true,
    createdAt = TestDb.now,
    category = Some("Category:Wiki Loves Monuments in Ukraine 2025 - Quantity")
  )
}
