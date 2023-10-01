package org.intracer.wmua.cmd

import controllers.Global.commons
import db.scalikejdbc._
import org.intracer.wmua._
import org.scalawiki.dto.Namespace
import play.api.Logging
import spray.util.pimpFuture

import scala.concurrent.duration._

case class DistributeImages(round: Round, images: Seq[Image], jurors: Seq[User])
    extends Logging {

  val sortedJurors = jurors.sorted

  def apply(): Unit = {
    val selection: Seq[Selection] = newSelection

    logger.debug("saving selection: " + selection.size)
    SelectionJdbc.batchInsert(selection)
    logger.debug(s"saved selection")

    addCriteriaRates(selection)
  }

  def newSelection: Seq[Selection] = {
    val selection: Seq[Selection] = round.distribution match {
      case 0 =>
        sortedJurors.flatMap { juror =>
          images.map(img => Selection(img, juror, round))
        }
      case x if x > 0 =>
        images.zipWithIndex.flatMap {
          case (img, i) =>
            (0 until x).map(j =>
              Selection(img, sortedJurors((i + j) % sortedJurors.size), round))
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

object DistributeImages extends Logging {
  def distributeImages(round: Round,
                       jurors: Seq[User],
                       prevRound: Option[Round],
                       removeUnrated: Boolean = false): Unit = {
    if (removeUnrated) {
      SelectionJdbc.removeUnrated(round.getId)
    }

    val images = getFilteredImages(round, jurors, prevRound)

    distributeImages(round, jurors, images)
  }

  def getFilteredImages(round: Round,
                        jurors: Seq[User],
                        prevRound: Option[Round]): Seq[Image] = {
    getFilteredImages(
      round,
      jurors,
      prevRound,
      selectedAtLeast = round.prevSelectedBy,
      selectMinAvgRating = round.prevMinAvgRate,
      selectTopByRating = round.topImages,
      includeCategory = round.category,
      excludeCategory = round.excludeCategory,
      includeRegionIds = round.regionIds.toSet,
      includeMonumentIds = round.monumentIds.toSet
    )
  }

  def distributeImages(round: Round,
                       jurors: Seq[User],
                       images: Seq[Image]): Unit = {
    DistributeImages(round, images, jurors).apply()
  }

  def categoryFileIds(maybeCategory: Option[String]): Iterable[Long] = {
    maybeCategory
      .filter(_.trim.nonEmpty)
      .map { category =>
        commons
          .page(category)
          .imageInfoByGenerator("categorymembers", "cm", Set(Namespace.FILE))
          .await(5.minutes)
          .flatMap(_.id)
      }
      .getOrElse(Nil)
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

    val includeFromCats = categoryFileIds(includeCategory)
    val excludeFromCats = categoryFileIds(excludeCategory)

    val currentImages = ImageJdbc
      .byRoundMerged(round.getId, rated = None)
      .filter(iwr => iwr.selection.nonEmpty)
      .toSet
    val existingImageIds = currentImages.map(_.pageId)
    val existingJurorIds = currentImages.flatMap(_.jurors)
    val mpxAtLeast = round.minMpx
    val sizeAtLeast = round.minImageSize.map(_ * 1024 * 1024)

    val contest = ContestJuryJdbc.findById(round.contestId).get
    val imagesAll = prevRound.fold[Seq[ImageWithRating]](
      ImageJdbc
        .findByContest(contest)
        .map(i => new ImageWithRating(i, Seq.empty))
    )(r => ImageJdbc.byRoundMerged(r.getId, rated = selectedAtLeast.map(_ > 0)))
    logger.debug("Total images: " + imagesAll.size)

    val funGens = ImageWithRatingSeqFilter.funGenerators(
      prevRound,
      includeRegionIds = includeRegionIds,
      excludeRegionIds = excludeRegionIds,
      includeMonumentIds = includeMonumentIds,
      includePageIds = includePageIds ++ includeFromCats.toSet,
      excludePageIds = excludePageIds ++ existingImageIds ++ excludeFromCats.toSet,
      includeTitles = includeTitles,
      excludeTitles = excludeTitles,
      includeJurorId = includeJurorId,
      excludeJurorId = excludeJurorId /*++ existingJurorIds*/,
      selectMinAvgRating = prevRound.flatMap(_ =>
        selectMinAvgRating.filter(x => !prevRound.exists(_.isBinary))),
      selectTopByRating = prevRound.flatMap(_ => selectTopByRating),
      selectedAtLeast = prevRound.flatMap(_ => selectedAtLeast),
      mpxAtLeast = mpxAtLeast,
      sizeAtLeast = sizeAtLeast,
      specialNomination = round.specialNomination
    )

    val filterChain = ImageWithRatingSeqFilter.makeFunChain(funGens)

    val images = filterChain(imagesAll).map(_.image)
    logger.debug("Images after filtering: " + images.size)

    images
  }

  case class Rebalance(newSelections: Seq[Selection],
                       removedSelections: Seq[Selection])

  val NoRebalance = Rebalance(Nil, Nil)

  def rebalanceImages(round: Round,
                      jurors: Seq[User],
                      images: Seq[Image],
                      currentSelection: Seq[Selection]): Rebalance = {

    if (currentSelection == Nil) {
      Rebalance(DistributeImages(round, images, jurors).newSelection, Nil)
    } else {
      Rebalance(Nil, Nil)
    }
  }

}
