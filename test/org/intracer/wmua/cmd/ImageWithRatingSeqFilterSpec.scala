package org.intracer.wmua.cmd

import db.scalikejdbc.Round
import org.specs2.mutable.Specification

class ImageWithRatingSeqFilterSpec extends Specification {

  val round = Some(Round(Some(5), 1, Some("Round"), 3))
  import ImageWithRatingSeqFilter._

  "ImageWithRatingSeqFilter" should {
    "funGenerators" in {

      funGenerators(round, includeRegionIds = Set("01")) === Seq(IncludeRegionIds(Set("01")))
      funGenerators(round, excludeRegionIds = Set("02")) === Seq(ExcludeRegionIds(Set("02")))

      funGenerators(round, includePageIds = Set(1)) === Seq(IncludePageIds(Set(1)))
      funGenerators(round, excludePageIds = Set(2)) === Seq(ExcludePageIds(Set(2)))

      funGenerators(round, includeTitles = Set("image1")) === Seq(IncludeTitles(Set("image1")))
      funGenerators(round, excludeTitles = Set("image2")) === Seq(ExcludeTitles(Set("image2")))

      funGenerators(round, includeJurorId = Set(3)) === Seq(IncludeJurorId(Set(3)))
      funGenerators(round, excludeJurorId = Set(4)) === Seq(ExcludeJurorId(Set(4)))

      funGenerators(round, selectTopByRating = Some(10)) === Seq(SelectTopByRating(10, round.get))
      funGenerators(round, selectedAtLeast = Some(2)) === Seq(SelectedAtLeast(2))
    }

  }
}
