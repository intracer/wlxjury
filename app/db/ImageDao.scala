package db

import org.intracer.wmua.{Image, ImageWithRating}

trait ImageDao {
  def batchInsert(images: Seq[Image]): Unit

  def updateResolution(pageId: Long, width: Int, height: Int): Unit

  def findAll(): Seq[Image]

  def findByContest(contest: Long): Seq[Image]

  def findByMonumentId(monumentId: String): Seq[Image]

  def find(id: Long): Option[Image]

  def byUserImageWithRating(userId: Long,
                            roundId: Long,
                            rate: Option[Int] = None,
                            pageSize: Int = Int.MaxValue,
                            offset: Int = 0,
                            startPageId: Option[Long] = None): Seq[ImageWithRating]

  def byUserRoundRateParamCount(userId: Long, roundId: Long, rate: Int): Int

  def byUserRoundRatedCount(userId: Long, roundId: Long): Int

  def byRating(rate: Int, roundId: Long): Seq[ImageWithRating]

  def byRatingGE(roundId: Long, rate: Int): Seq[ImageWithRating]

  def byRound(roundId: Long, pageSize: Int = Int.MaxValue, offset: Int = 0): Seq[ImageWithRating]

  def byRatingMerged(rate: Int, round: Long): Seq[ImageWithRating]

  def byRatingGEMerged(rate: Int, round: Long): Seq[ImageWithRating]

  def byRoundMerged(round: Long, pageSize: Int = Int.MaxValue, offset: Int = 0): Seq[ImageWithRating]

  def byRoundSummed(roundId: Long, pageSize: Int = Int.MaxValue, offset: Int = 0, startPageId: Option[Long] = None): Seq[ImageWithRating]

}
