package models

import org.intracer.wmua.ContestJury
import org.specs2.mutable.Specification


class ContestTest extends Specification  {

  "fresh database" should {

    "be ok" in {
      val contests = ContestJury.findAll()
      contests must not (beEmpty)
    }
  }

}
