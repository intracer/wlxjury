package db

import org.intracer.wmua.{Image, ImageWithRating, User}

import scala.concurrent.Future

trait ImageDao {
  def batchInsert(images: Seq[Image]): Future[Unit]

  def updateResolution(pageId: Long, width: Int, height: Int): Future[Unit]

  def findAll(): Future[List[Image]]

  def findByContest(contest: Long): Future[List[Image]]

  def findByMonumentId(monumentId: String): Future[List[Image]]

  def find(id: Long): Future[Image]
  def bySelection(round: Long): Future[List[Image]]

  def bySelectionNotSelected(round: Long): Future[List[Image]]

  def bySelectionSelected(round: Long): Future[List[Image]]

  def byUser(user: User, roundId: Long): Future[Seq[Image]]

  def byUserSelected(user: User, roundId: Long): Future[Seq[Image]]

  def findWithSelection(id: Long, roundId: Long): Future[Seq[ImageWithRating]]

  def byUserImageWithRating(user: User, roundId: Long): Future[Seq[ImageWithRating]]

  def byRating(roundId: Long, rate: Int): Future[Seq[ImageWithRating]]

  def byRatingGE(roundId: Long, rate: Int): Future[Seq[ImageWithRating]]

  def byRound(roundId: Long): Future[Seq[ImageWithRating]]

  def byRatingMerged(rate: Int, round: Int): Future[Seq[ImageWithRating]]

  def byRatingGEMerged(rate: Int, round: Int): Future[Seq[ImageWithRating]]

  def byRoundMerged(round: Int): Future[Seq[ImageWithRating]]

  def byRoundSummed(roundId: Long): Future[Seq[ImageWithRating]]

}
