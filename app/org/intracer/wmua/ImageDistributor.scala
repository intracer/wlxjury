package org.intracer.wmua

import db.scalikejdbc.{ImageJdbc, RoundJdbc, SelectionJdbc}
import org.joda.time.DateTime
import org.scalawiki.MwBot

import scala.concurrent.Await

object ImageDistributor {

  def getImages(contestId: Long, round: Round): Seq[Image] = {
    val allImages: Seq[Image] = if (round.number == 1) {
      val fromDb = ImageJdbc.findByContest(contestId)


      val filtered = fromDb

      val fromDbIds = filtered.map(_.pageId).toSet

      val ids = fromDbIds
      fromDb.filter(i => ids.contains(i.pageId))
    } else {
        val rounds = RoundJdbc.findByContest(contestId)
        (for (prevRound <- rounds.find(_.number == round.number - 1)) yield {
          ImageJdbc.byRatingMerged(1, prevRound.id.get).map(_.image)
        }).getOrElse(Seq.empty)
    }
    allImages
  }

  def fromCategory: Set[Long] = {
    import scala.concurrent.duration._

    val commons = MwBot.fromHost(MwBot.commons)

    val query = commons.page("Category:Non-photographic media from European Science Photo Competition 2015")
    val future = query.imageInfoByGenerator("categorymembers", "cm",
      props = Set("timestamp", "user", "size", "url"),
      titlePrefix = None)

    val filesInCategory = Await.result(future, 15.minutes)

    val categoryIds = filesInCategory.flatMap(_.id).toSet
    categoryIds
  }

  def createNextRound(round: Round, jurors: Seq[User], prevRound: Round) = {
    val newImages = ImageJdbc.byRatingMerged(1, round.id.get)
    if (false && newImages.isEmpty) {

      val images =
      //ImageJdbc.byRoundMerged(prevRound.id.toInt).filter(_.image.region.exists(r => !selectedRegions.contains(r))) ++
        ImageJdbc.findAll().filter(_.region.contains("44"))
      //
      val selection = jurors.flatMap { juror =>
        images.map(img => new Selection(0, img.pageId, 0, juror.id.get, round.id.get, DateTime.now))
      }

      SelectionJdbc.batchInsert(selection)

    }
  }
}
