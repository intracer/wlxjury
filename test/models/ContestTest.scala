package models

import org.intracer.wmua.ContestJury
import org.specs2.mutable.Specification
import play.api.test._
import play.api.test.Helpers._


class ContestTest extends Specification {

  "fresh database" should {

    "be ok" in {

      running(FakeApplication()) {

        val contests = ContestJury.findAll()

        contests must beEmpty
      }

    }

  }


}
