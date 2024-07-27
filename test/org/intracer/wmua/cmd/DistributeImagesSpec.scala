package org.intracer.wmua.cmd

import db.ImageRepo
import db.scalikejdbc.Round
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class DistributeImagesSpec extends Specification with Mockito {

  "getFilteredImages" should {
    "filter by media type" in {
      val roundId = 1L
      val contestId = 2L
      val round = new Round(Some(roundId), 1, contestId = contestId)
      val repo = mock[ImageRepo]
      repo.byRoundMerged(roundId).returns(Nil)
      repo.findByContestId(contestId).returns(Nil)

      val di = new DistributeImages(repo)
      val images = di.getFilteredImages(round, None)
      images === Nil
    }
  }

}
