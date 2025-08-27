package db

import org.intracer.wmua.{ContestJury, Image, ImageWithRating}

trait ImageRepo {

  def findByContestId(contestId: Long): List[Image]

  def byRoundMerged(
      roundId: Long,
      pageSize: Int = Int.MaxValue,
      offset: Int = 0,
      rated: Option[Boolean] = None
  ): Seq[ImageWithRating]
}
