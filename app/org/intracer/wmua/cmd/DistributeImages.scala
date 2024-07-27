package org.intracer.wmua.cmd

import controllers.Global.commons
import db.ImageRepo
import db.scalikejdbc._
import org.intracer.wmua._
import org.intracer.wmua.cmd.DistributeImages.Rebalance
import org.scalawiki.dto.Namespace
import play.api.Logging
import spray.util.pimpFuture

import scala.concurrent.duration._

class DistributeImages(imageRepo: ImageRepo) extends Logging {

  def distributeImages(round: Round, images: Seq[Image], jurors: Seq[User]): Unit = {
    val selection: Seq[Selection] = newSelection(round, images, jurors)

    logger.debug("saving selection: " + selection.size)
    SelectionJdbc.batchInsert(selection)
    logger.debug(s"saved selection")

    if (round.hasCriteria) {
      addCriteriaRates(selection)
    }
  }

  def newSelection(round: Round, images: Seq[Image], jurors: Seq[User]): Seq[Selection] = {
    val sortedJurors = jurors.sorted
    val selection: Seq[Selection] = round.distribution match {
      case 0 =>
        sortedJurors.flatMap { juror =>
          images.map(img => Selection(img, juror, round))
        }
      case x if x > 0 =>
        images.zipWithIndex.flatMap { case (img, i) =>
          (0 until x).map(j => Selection(img, sortedJurors((i + j) % sortedJurors.size), round))
        }
    }
    selection
  }

  def addCriteriaRates(selection: Seq[Selection]): Unit = {
    val criteriaIds = Seq(6, 7, 8, 9) // TODO load form DB
    val rates = selection.flatMap { s =>
      criteriaIds.map(id => new CriteriaRate(0, s.getId, id, 0))
    }

    CriteriaRate.batchInsert(rates)
  }

  def distributeImages(
      round: Round,
      jurors: Seq[User],
      prevRound: Option[Round],
      removeUnrated: Boolean = false
  ): Unit = {
    if (removeUnrated) {
      SelectionJdbc.removeUnrated(round.getId)
    }

    val images = getFilteredImages(round, prevRound)

    distributeImages(round, images, jurors)
  }

  def getFilteredImages(round: Round, prevRound: Option[Round]): Seq[Image] = {
    getFilteredImages(
      round,
      prevRound,
      selectedAtLeast = round.prevSelectedBy,
      selectMinAvgRating = round.prevMinAvgRate,
      selectTopByRating = round.topImages,
      includeCategory = round.category,
      excludeCategory = round.excludeCategory,
      includeRegionIds = round.regionIds.toSet,
      includeMonumentIds = round.monumentIds.toSet,
      mediaType = round.mediaType
    )
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
      prevRound: Option[Round],
      includeRegionIds: Set[String] = Set.empty,
      excludeRegionIds: Set[String] = Set.empty,
      includeMonumentIds: Set[String] = Set.empty,
      includePageIds: Set[Long] = Set.empty,
      excludePageIds: Set[Long] = Set.empty,
      includeTitles: Set[String] = Set.empty,
      excludeTitles: Set[String] = Set.empty,
      selectMinAvgRating: Option[BigDecimal] = None,
      selectTopByRating: Option[Int] = None,
      selectedAtLeast: Option[Int] = None,
      includeJurorId: Set[Long] = Set.empty,
      excludeJurorId: Set[Long] = Set.empty,
      includeCategory: Option[String] = None,
      excludeCategory: Option[String] = None,
      mediaType: Option[String] = None
  ): Seq[Image] = {

    val includeFromCats = categoryFileIds(includeCategory)
    val excludeFromCats = categoryFileIds(excludeCategory)

    val currentImages = imageRepo
      .byRoundMerged(round.getId)
      .filter(iwr => iwr.selection.nonEmpty)
      .toSet
    val existingImageIds = currentImages.map(_.pageId)
    val existingJurorIds = currentImages.flatMap(_.jurors)
    val mpxAtLeast = round.minMpx
    val sizeAtLeast = round.minImageSize.map(_ * 1024 * 1024)

    val imagesAll = prevRound.fold[Seq[ImageWithRating]](
      imageRepo
        .findByContestId(round.contestId)
        .map(i => new ImageWithRating(i, Seq.empty))
    )(r => imageRepo.byRoundMerged(r.getId, rated = selectedAtLeast.map(_ > 0)))
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
      selectMinAvgRating =
        prevRound.flatMap(_ => selectMinAvgRating.filter(_ => !prevRound.exists(_.isBinary))),
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

  def rebalanceImages(
      round: Round,
      jurors: Seq[User],
      images: Seq[Image],
      currentSelection: Seq[Selection]
  ): Rebalance = {

    if (currentSelection == Nil) {
      Rebalance(newSelection(round, images, jurors), Nil)
    } else {
      Rebalance(Nil, Nil)
    }
  }

}

object DistributeImages {

  case class Rebalance(newSelections: Seq[Selection], removedSelections: Seq[Selection])

  val NoRebalance = Rebalance(Nil, Nil)

}
