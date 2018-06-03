package org.intracer.wmua.cmd

import java.time.ZonedDateTime

import db.scalikejdbc.SelectionJdbc
import org.intracer.wmua._

case class DistributeImages(round: Round, images: Seq[Image], jurors: Seq[User]) {

  def apply() = {
    val selection: Seq[Selection] = round.distribution match {
      case 0 =>
        jurors.flatMap { juror =>
          images.map(img => Selection(0, img.pageId, 0, juror.id.get, round.id.get, ZonedDateTime.now))
        }
      case x if x > 0 =>
        images.zipWithIndex.flatMap {
          case (img, i) =>
            (0 until x).map(j =>
              Selection(0, img.pageId, 0, jurors((i + j) % jurors.size).id.get, round.id.get, ZonedDateTime.now)
          )
        }
    }

    SelectionJdbc.removeUnrated(round.id.get)

    println("saving selection: " + selection.size)
    SelectionJdbc.batchInsert(selection)
    println(s"saved selection")

    addCriteriaRates(selection)
  }

  def addCriteriaRates(selection: Seq[Selection]): Unit = {
    if (round.hasCriteria) {
      val criteriaIds = Seq(1, 2, 3, 4) // TODO load form DB
      val rates = selection.flatMap { s =>
          criteriaIds.map(id => new CriteriaRate(0, s.id, id, 0))
        }

      CriteriaRate.batchInsert(rates)
    }
  }
}
