package org.intracer.wmua.cmd

import db.scalikejdbc.Round
import org.intracer.wmua.cmd.ImageWithRatingSeqFilter.ImageFilter
import org.intracer.wmua.{Image, ImageWithRating}
import org.scalawiki.wlx.MonumentDB
import org.scalawiki.wlx.dto.{Contest, SpecialNomination}
import org.scalawiki.wlx.query.MonumentQuery
import org.scalawiki.wlx.stat.ContestStat
import play.api.Logging

import scala.runtime.ScalaRunTime

trait ImageFilterGen extends (() => ImageFilter) with Product with Logging {

  override def toString: String = ScalaRunTime._toString(this)

  def imageFilter(p: Image => Boolean): ImageFilter =
    (images: Seq[ImageWithRating]) => {
      val result = images.filter(i => p(i.image))
      logger.debug(
        s"imageFilter: ${toString()}\n images before: ${images.size}, images after: ${result.size}")
      result
    }

  def imageRatingFilter(p: ImageWithRating => Boolean): ImageFilter =
    (images: Seq[ImageWithRating]) => {
      val result = images.filter(p)
      logger.debug(
        s"imageRatingFilter: ${toString()}\n images before: ${images.size}, images after: ${result.size}")
      result
    }

}

case class IncludeRegionIds(regionIds: Set[String]) extends ImageFilterGen {
  def apply(): ImageFilter =
    imageFilter(_.region.exists(regionIds.contains))
}

case class IncludeMonumentIds(monumentIds: Set[String]) extends ImageFilterGen {
  def apply(): ImageFilter =
    imageFilter(_.monumentId.exists(monumentIds.contains))
}

case class ExcludeRegionIds(regionIds: Set[String]) extends ImageFilterGen {
  def apply(): ImageFilter =
    imageFilter(!_.region.exists(regionIds.contains))
}

case class IncludePageIds(pageIds: Set[Long]) extends ImageFilterGen {
  def apply(): ImageFilter = imageFilter(i => pageIds.contains(i.pageId))
}

case class ExcludePageIds(pageIds: Set[Long]) extends ImageFilterGen {
  def apply(): ImageFilter = imageFilter(i => !pageIds.contains(i.pageId))
}

case class IncludeTitles(titles: Set[String]) extends ImageFilterGen {
  def apply(): ImageFilter = imageFilter(i => titles.contains(i.title))
}

case class ExcludeTitles(titles: Set[String]) extends ImageFilterGen {
  def apply(): ImageFilter = imageFilter(i => titles.contains(i.title))
}

case class IncludeJurorId(jurors: Set[Long]) extends ImageFilterGen {
  def apply(): ImageFilter =
    imageRatingFilter(
      i => i.selection.map(_.juryId).toSet.intersect(jurors).nonEmpty)
}

case class ExcludeJurorId(jurors: Set[Long]) extends ImageFilterGen {
  def apply(): ImageFilter =
    imageRatingFilter(
      i => i.selection.map(_.juryId).toSet.intersect(jurors).isEmpty)
}

case class SelectTopByRating(topN: Int, round: Round) extends ImageFilterGen {
  def apply(): ImageFilter =
    (images: Seq[ImageWithRating]) =>
      images.sortBy(-_.totalRate(round)).take(topN)
}

case class SelectMinAvgRating(rate: Int, round: Round) extends ImageFilterGen {
  def apply(): ImageFilter = imageRatingFilter(i => i.totalRate(round) >= rate)
}

case class SelectedAtLeast(by: Int) extends ImageFilterGen {
  def apply(): ImageFilter = imageRatingFilter(i => i.rateSum >= by)
}

case class MegaPixelsAtLeast(mpx: Int) extends ImageFilterGen {
  def apply(): ImageFilter = imageFilter(_.mpx >= mpx)
}

case class SizeAtLeast(size: Int) extends ImageFilterGen {
  def apply(): ImageFilter = imageFilter(_.size.exists(_ >= size))
}

case class SpecialNominationFilter(specialNominationName: String)
    extends ImageFilterGen {
  val specialNominationIds: Set[String] = SpecialNomination.nominations
    .find(_.name == specialNominationName)
    .map { nomination =>
      val contest = Contest.WLMUkraine(2020)
      val stat = if (nomination.cities.nonEmpty) {
        ContestStat(contest, 2012).copy(monumentDb = Some(
          MonumentDB.getMonumentDb(contest, MonumentQuery.create(contest))))
      } else {
        ContestStat(contest, 2012)
      }
      val map = SpecialNomination.getMonumentsMap(Seq(nomination), stat)
      map.values.flatten.map(_.id).toSet
    }
    .getOrElse(Set.empty)

  def apply(): ImageFilter =
    imageFilter(_.monumentId.exists(specialNominationIds.contains))
}

object ImageWithRatingSeqFilter {
  type ImageFilter = Seq[ImageWithRating] => Seq[ImageWithRating]
  def funGenerators(
      round: Option[Round] = None,
      includeRegionIds: Set[String] = Set.empty,
      excludeRegionIds: Set[String] = Set.empty,
      includeMonumentIds: Set[String] = Set.empty,
      includePageIds: Set[Long] = Set.empty,
      excludePageIds: Set[Long] = Set.empty,
      includeTitles: Set[String] = Set.empty,
      excludeTitles: Set[String] = Set.empty,
      includeJurorId: Set[Long] = Set.empty,
      excludeJurorId: Set[Long] = Set.empty,
      selectMinAvgRating: Option[Int] = None,
      selectTopByRating: Option[Int] = None,
      selectedAtLeast: Option[Int] = None,
      mpxAtLeast: Option[Int] = None,
      sizeAtLeast: Option[Int] = None,
      specialNomination: Option[String] = None): Seq[ImageFilterGen] = {

    val setMap = Map(
      IncludeRegionIds(includeRegionIds) -> includeRegionIds,
      ExcludeRegionIds(excludeRegionIds) -> excludeRegionIds,
      IncludeMonumentIds(includeMonumentIds) -> includeMonumentIds,
      IncludePageIds(includePageIds) -> includePageIds,
      ExcludePageIds(excludePageIds) -> excludePageIds,
      IncludeTitles(includeTitles) -> includeTitles,
      ExcludeTitles(excludeTitles) -> excludeTitles,
      IncludeJurorId(includeJurorId) -> includeJurorId,
      ExcludeJurorId(excludeJurorId) -> excludeJurorId
    )

    val optionMap = Map(
      selectMinAvgRating
        .map(top => SelectMinAvgRating(top, round.get)) -> selectMinAvgRating,
      selectTopByRating
        .map(top => SelectTopByRating(top, round.get)) -> selectTopByRating,
      selectedAtLeast.map(n => SelectedAtLeast(n)) -> selectedAtLeast,
      mpxAtLeast.map(MegaPixelsAtLeast) -> mpxAtLeast,
      sizeAtLeast.map(SizeAtLeast) -> sizeAtLeast,
      specialNomination.map(SpecialNominationFilter) -> specialNomination
    )

    (setMap
      .filter(_._2.nonEmpty)
      .keys ++ optionMap.filter(_._2.nonEmpty).keys.flatten).toSeq
  }

  def makeFunChain(
      gens: Seq[ImageFilterGen]): Seq[ImageWithRating] => Seq[ImageWithRating] =
    Function.chain(gens.map(_.apply))
}
