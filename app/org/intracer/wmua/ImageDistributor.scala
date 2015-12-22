package org.intracer.wmua

import db.scalikejdbc.{ImageJdbc, RoundJdbc, SelectionJdbc}
import org.joda.time.DateTime
import org.scalawiki.MwBot

import scala.concurrent.Await

object ImageDistributor {

  def distributeImages(contestId: Long, round: Round) {

    val allImages: Seq[Image] = getImages(contestId, round)

    val allJurors = round.jurors

    val currentSelection = ImageJdbc.byRoundMerged(round.id.get)
    //      Selection.

    val oldImagesSelection = currentSelection.filter(iwr => iwr.selection.nonEmpty).toSet
    val oldImageIds = oldImagesSelection.map(iwr => iwr.pageId).toSeq
    val oldJurorIds: Set[Long] = oldImagesSelection.toSet.flatMap {
      iwr: ImageWithRating =>
        iwr.selection.map(s => s.juryId).toSet
    }

    val images = allImages.filterNot(i => oldImageIds.contains(i.pageId))
//      .filter{
//      i => i.monumentId.exists(id => id.startsWith("44") || id.startsWith("32") || id.startsWith("65") || id.startsWith("63"))
//    }
    val jurors = allJurors.filterNot(j => oldJurorIds.contains(j.id.get))

    val selection: Seq[Selection] = round.distribution match {
      case 0 =>
        jurors.flatMap { juror =>
          images.map(img => new Selection(0, img.pageId, 0, juror.id.get, round.id.get, DateTime.now))
        }
      case 1 =>
        images.zipWithIndex.map {
          case (img, i) => new Selection(0, img.pageId, 0, jurors(i % jurors.size).id.get, round.id.get, DateTime.now)
        }
      //          jurors.zipWithIndex.flatMap { case (juror, i) =>
      //            images.slice(i * perJuror, (i + 1) * perJuror).map(img => new Selection(0, img.pageId, 0, juror.id, round.id, DateTime.now))
      //          }
      case 2 =>
        images.zipWithIndex.flatMap {
          case (img, i) => Seq(
            new Selection(0, img.pageId, 0, jurors(i % jurors.size).id.get, round.id.get, DateTime.now),
            new Selection(0, img.pageId, 0, jurors((i + 1) % jurors.size).id.get, round.id.get, DateTime.now)
          )
        }

      case 3 =>
        images.zipWithIndex.flatMap {
          case (img, i) => Seq(
            new Selection(0, img.pageId, 0, jurors(i % jurors.size).id.get, round.id.get, DateTime.now),
            new Selection(0, img.pageId, 0, jurors((i + 1) % jurors.size).id.get, round.id.get, DateTime.now),
            new Selection(0, img.pageId, 0, jurors((i + 2) % jurors.size).id.get, round.id.get, DateTime.now)
          )
        }
      //          jurors.zipWithIndex.flatMap { case (juror, i) =>
      //            imagesTwice.slice(i * perJuror, (i + 1) * perJuror).map(img => new Selection(0, img.pageId, 0, juror.id, round.id, DateTime.now))
      //          }
    }
    println("saving selection: " + selection.size)
    SelectionJdbc.batchInsert(selection)
    println(s"saved selection")

  }

  def getImages(contestId: Long, round: Round): Seq[Image] = {
    val allImages: Seq[Image] = if (round.number == 1) {
      val fromDb = ImageJdbc.findByContest(contestId)

//      val selection =

      val filtered = fromDb//.filter(i => selection.contains(i.title))

      /*
      val categoryIds: Set[Long] = fromCategory
*/
      val fromDbIds = filtered.map(_.pageId).toSet

      val ids = fromDbIds //intersect categoryIds

      //      ids.foreach{
      //        id =>
      //
      //          SelectionJdbc.setRound(id, round.id.get, 26, 40)
      //      }
      //      val largeIds = filesInCategory.filter(_.images.headOption.exists(_.size.exists(_ > 1024 * 1024))).flatMap(_.id).toSet
      fromDb.filter(i => ids.contains(i.pageId))
    } else {
      if (true) {
        val rounds = RoundJdbc.findByContest(contestId)
        (for (prevRound <- rounds.find(_.number == round.number - 1)) yield {
          ImageJdbc.byRatingMerged(1, prevRound.id.get).map(_.image)
        }).getOrElse(Seq.empty)
      } else {
        val rounds = RoundJdbc.findByContest(contestId)
        val prevRound = rounds.find(_.number == round.number - 1).get

        val rate = Some(1)
        val users = 3
        val images = prevRound.allImages
        val selection = SelectionJdbc.byRound(prevRound.id.get)
        val ratedSelection = rate.fold(selection)(r => selection.filter(_.rate == r))

        val byPageId = ratedSelection.groupBy(_.pageId).filter(_._2.size == users)

        val imagesWithSelection = images.flatMap {
          image =>
            if (byPageId.contains(image.pageId)) {
              Some(image.image)
            } else {
              None
            }
        }
        imagesWithSelection
      }
    }
    allImages
  }

  def fromCategory: Set[Long] = {
    import scala.concurrent.duration._

    val commons = MwBot.get(MwBot.commons)

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


      //      val selectedRegions = Set("01", "07", "14", "21", "26", "44", "48", "74")
      //
      val images =
      //ImageJdbc.byRoundMerged(prevRound.id.toInt).filter(_.image.region.exists(r => !selectedRegions.contains(r))) ++
        ImageJdbc.findAll().filter(_.region.contains("44"))
      //
      val selection = jurors.flatMap { juror =>
        images.map(img => new Selection(0, img.pageId, 0, juror.id.get, round.id.get, DateTime.now))
      }

      //      val images = ImageJdbc.byRoundMerged(prevRound.id.toInt).sortBy(-_.totalRate).take(21)
      //      val selection = images.flatMap(_.selection).map(_.copy(id = 0, round = round.id))

      SelectionJdbc.batchInsert(selection)

    }
  }
}
