package org.intracer.wmua

import org.specs2.mutable.Specification

class ImageDistributorSpec extends Specification {

  val contest: ContestJury = ContestJury(Some(1), "WLE", 2015, "Ukraine", None, 2, None)

  val round: Round = Round(Some(2), 1, None, 1)

  "ImageDistributorTest" should {
    "createNextRound" in {

      ImageDistributor.distributeImages(contest, round)
      ok
    }

    "distributeImages" in {
      ok
    }

  }
}
