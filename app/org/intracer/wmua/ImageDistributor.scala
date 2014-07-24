package org.intracer.wmua

import org.joda.time.DateTime

object ImageDistributor {

  def distributeImages(contest: Contest, round: Round) {

    val images: Seq[Image] = if (round.number == 1) {
      Image.findByContest(contest.id)
    } else {
      val rounds = Round.findByContest(contest.id)
      (for (prevRound <- rounds.find(_.number == round.number - 1)) yield {
        Image.byRatingMerged(1, prevRound.id.toInt).map(_.image)
      }).getOrElse(Seq.empty)
    }

    val jurors = round.jurors

    val currentSelection = Image.byRatingMerged(1, round.id.toInt)
    if (currentSelection.isEmpty) {
//      Selection.

      val selection: Seq[Selection] = round.distribution match {
        case 0 =>
          jurors.flatMap { juror =>
            images.map(img => new Selection(0, img.pageId, 0, juror.id, round.id, DateTime.now))
          }
        case 1 =>
          images.zipWithIndex.map {
            case (img, i) => new Selection(0, img.pageId, 0, jurors(i % jurors.size).id, round.id, DateTime.now)
          }
        //          jurors.zipWithIndex.flatMap { case (juror, i) =>
        //            images.slice(i * perJuror, (i + 1) * perJuror).map(img => new Selection(0, img.pageId, 0, juror.id, round.id, DateTime.now))
        //          }

        case 2 =>
          images.zipWithIndex.flatMap {
            case (img, i) => Seq(
              new Selection(0, img.pageId, 0, jurors(i % jurors.size).id, round.id, DateTime.now),
              new Selection(0, img.pageId, 0, jurors((i + 1) % jurors.size).id, round.id, DateTime.now)
            )
          }
        //          jurors.zipWithIndex.flatMap { case (juror, i) =>
        //            imagesTwice.slice(i * perJuror, (i + 1) * perJuror).map(img => new Selection(0, img.pageId, 0, juror.id, round.id, DateTime.now))
        //          }
      }
      Selection.batchInsert(selection)
    }
  }

  def createNextRound(round: Round, jurors: Seq[User], prevRound: Round) = {
    val newImages = Image.byRatingMerged(1, round.id.toInt)
    if (false && newImages.isEmpty) {

      //      val selectedRegions = Set("01", "07", "14", "21", "26", "44", "48", "74")
      //
      val images =
      //Image.byRoundMerged(prevRound.id.toInt).filter(_.image.region.exists(r => !selectedRegions.contains(r))) ++
        Image.findAll().filter(_.region == Some("44"))
      //
      val selection = jurors.flatMap { juror =>
        images.map(img => new Selection(0, img.pageId, 0, juror.id, round.id, DateTime.now))
      }

      //      val images = Image.byRoundMerged(prevRound.id.toInt).sortBy(-_.totalRate).take(21)
      //      val selection = images.flatMap(_.selection).map(_.copy(id = 0, round = round.id))

      Selection.batchInsert(selection)


    }
  }


}
