package org.intracer.wmua.cmd

import controllers.Global.commons
import db.scalikejdbc.{ContestJuryJdbc, ImageJdbc, SelectionJdbc}
import org.intracer.wmua._
import org.scalawiki.dto.Namespace
import play.api.Logger
import spray.util.pimpFuture

case class DistributeImages(round: Round, images: Seq[Image], jurors: Seq[User]) {

  def apply() = {
    val selection: Seq[Selection] = newSelection

    SelectionJdbc.removeUnrated(round.getId)

    Logger.logger.debug("saving selection: " + selection.size)
    SelectionJdbc.batchInsert(selection)
    Logger.logger.debug(s"saved selection")

    addCriteriaRates(selection)
  }

  def newSelection = {
    val selection: Seq[Selection] = round.distribution match {
      case 0 =>
        jurors.flatMap { juror =>
          images.map(img => Selection(img, juror, round))
        }
      case x if x > 0 =>
        images.zipWithIndex.flatMap {
          case (img, i) =>
            (0 until x).map(j =>
              Selection(img, jurors((i + j) % jurors.size), round)
            )
        }
    }
    selection
  }

  def addCriteriaRates(selection: Seq[Selection]): Unit = {
    if (round.hasCriteria) {
      val criteriaIds = Seq(1, 2, 3, 4) // TODO load form DB
      val rates = selection.flatMap { s =>
          criteriaIds.map(id => new CriteriaRate(0, s.getId, id, 0))
        }

      CriteriaRate.batchInsert(rates)
    }
  }
}

object DistributeImages {
  def distributeImages(round: Round,
                       jurors: Seq[User],
                       prevRound: Option[Round]): Unit = {
    val images = getFilteredImages(round, jurors, prevRound)

    distributeImages(round, jurors, images)
  }

  def getFilteredImages(round: Round, jurors: Seq[User], prevRound: Option[Round]): Seq[Image] = {
    getFilteredImages(round, jurors, prevRound, selectedAtLeast = round.prevSelectedBy,
      selectMinAvgRating = round.prevMinAvgRate,
      selectTopByRating = round.topImages,
      includeCategory = round.category,
      excludeCategory = round.excludeCategory,
      includeRegionIds = round.regionIds.toSet,
      includeMonumentIds = round.monumentIds.toSet
    )
  }

  def distributeImages(round: Round, jurors: Seq[User], images: Seq[Image]): Unit = {
    DistributeImages(round, images, jurors).apply()
  }

  def getFilteredImages(
                         round: Round,
                         jurors: Seq[User],
                         prevRound: Option[Round],
                         includeRegionIds: Set[String] = Set.empty,
                         excludeRegionIds: Set[String] = Set.empty,
                         includeMonumentIds: Set[String] = Set.empty,
                         includePageIds: Set[Long] = Set.empty,
                         excludePageIds: Set[Long] = Set.empty,
                         includeTitles: Set[String] = Set.empty,
                         excludeTitles: Set[String] = Set.empty,
                         selectMinAvgRating: Option[Int] = None,
                         selectTopByRating: Option[Int] = None,
                         selectedAtLeast: Option[Int] = None,
                         includeJurorId: Set[Long] = Set.empty,
                         excludeJurorId: Set[Long] = Set.empty,
                         includeCategory: Option[String] = None,
                         excludeCategory: Option[String] = None
                       ): Seq[Image] = {

    val includeFromCats = includeCategory.filter(_.trim.nonEmpty).map { category =>
      val pages = commons.page(category).imageInfoByGenerator("categorymembers", "cm", Set(Namespace.FILE)).await
      pages.flatMap(_.id)
    }.getOrElse(Nil)

    val excludeFromCats = excludeCategory.filter(_.trim.nonEmpty).map { category =>
      val pages = commons.page(category).imageInfoByGenerator("categorymembers", "cm", Set(Namespace.FILE)).await
      pages.flatMap(_.id)
    }.getOrElse(Nil)

    val currentSelection = ImageJdbc.byRoundMerged(round.getId, rated = Some(true)).filter(iwr => iwr.selection.nonEmpty).toSet
    val existingImageIds = currentSelection.map(_.pageId)
    val existingJurorIds = currentSelection.flatMap(_.jurors)
    val mpxAtLeast = round.minMpx
    val sizeAtLeast = round.minImageSize.map(_ * 1024 * 1024)

    val contest = ContestJuryJdbc.findById(round.contestId).get
    val imagesAll = prevRound.fold[Seq[ImageWithRating]](
      ImageJdbc.findByContest(contest).map(i =>
        new ImageWithRating(i, Seq.empty)
      )
    )(r =>
      ImageJdbc.byRoundMerged(r.getId, rated = selectedAtLeast.map(_ => true))
    )
    Logger.logger.debug("Total images: " + imagesAll.size)

    val funGens = ImageWithRatingSeqFilter.funGenerators(prevRound,
      includeRegionIds = includeRegionIds,
      excludeRegionIds = excludeRegionIds,
      includeMonumentIds = includeMonumentIds,
      includePageIds = includePageIds ++ includeFromCats.toSet,
      excludePageIds = excludePageIds ++ existingImageIds ++ excludeFromCats.toSet,
      includeTitles = includeTitles,
      excludeTitles = excludeTitles,
      includeJurorId = includeJurorId,
      excludeJurorId = excludeJurorId /*++ existingJurorIds*/ ,
      selectMinAvgRating = prevRound.flatMap(_ => selectMinAvgRating.filter(x => !prevRound.exists(_.isBinary))),
      selectTopByRating = prevRound.flatMap(_ => selectTopByRating),
      selectedAtLeast = prevRound.flatMap(_ => selectedAtLeast),
      mpxAtLeast = mpxAtLeast,
      sizeAtLeast = sizeAtLeast
    )

    val filterChain = ImageWithRatingSeqFilter.makeFunChain(funGens)

    val images = filterChain(imagesAll).map(_.image)
    Logger.logger.debug("Images after filtering: " + images.size)

    images
  }

  case class Rebalance(newSelections: Seq[Selection], removedSelections: Seq[Selection])

  val NoRebalance = Rebalance(Nil, Nil)

  def rebalanceImages(round: Round, jurors: Seq[User], images: Seq[Image], currentSelection: Seq[Selection]): Rebalance  = {

    if (currentSelection == Nil) {
      Rebalance(DistributeImages(round, images, jurors).newSelection, Nil)
    } else {
      Rebalance(Nil, Nil)
    }
  }

}
