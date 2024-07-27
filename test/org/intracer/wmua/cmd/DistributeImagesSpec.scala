package org.intracer.wmua.cmd

import db.ImageRepo
import db.scalikejdbc.Round
import org.intracer.wmua.Image
import org.intracer.wmua.cmd.DistributeImagesSpec.{di, files, image, round, video}
import org.specs2.mock.Mockito
import org.specs2.mock.Mockito.{mock, theStubbed}
import org.specs2.mutable.Specification

class DistributeImagesSpec extends Specification with Mockito {

  "imagesByRound" should {
    "work with no images" in {
      di(Nil).imagesByRound(round) === Nil
    }

    "do not filter" in {
      di(files).imagesByRound(round) === files
    }

    "filter by image media type" in {
      di(files).imagesByRound(round.copy(mediaType = Some("image"))) === List(image)
    }

    "filter by video media type" in {
      di(files).imagesByRound(round.copy(mediaType = Some("video"))) === List(video)
    }
  }

}

object DistributeImagesSpec {
  private val roundId = 1L
  private val contestId = 2L
  private val round = new Round(Some(roundId), 1, contestId = contestId)
  private val image = Image(1L, "File:1.jpg", mime = Some("image/jpeg"))
  private val video = Image(1L, "File:1.jpg", mime = Some("video/mpeg"))
  private val files = List(image, video)

  def di(images: List[Image]) = new DistributeImages(mockRepo(images))

  def mockRepo(images: List[Image]): ImageRepo = {
    val repo = mock[ImageRepo]
    repo.byRoundMerged(roundId).returns(Nil)
    repo.findByContestId(contestId).returns(images)
    repo
  }

}
