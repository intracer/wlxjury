package models

import db.scalikejdbc.ContestJuryJdbc
import org.intracer.wmua.ContestJury
import org.specs2.mutable.Specification


class ContestSpec extends Specification with WithTestDatabase {

  "fresh database" should {

    "be ok" in {
      val contests = ContestJuryJdbc.findAll()
      contests must not (beEmpty)
    }
  }

}
