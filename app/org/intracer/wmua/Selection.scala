package org.intracer.wmua

import java.time.ZonedDateTime

import db.scalikejdbc.{Round, SelectionJdbc}
import org.scalawiki.dto.Page

case class Selection(pageId: Long, juryId: Long, roundId: Long,
                     var rate: Int = 0, id: Option[Long] = None,
                     createdAt: Option[ZonedDateTime] = None,
                     deletedAt: Option[ZonedDateTime] = None,
                     criteriaId: Option[Int] = None) extends HasId {

  def destroy() = SelectionJdbc.destroy(pageId, juryId, roundId)

}

object Selection {

  def apply(img: Image, juror: User, round: Round, rate: Int): Selection =
    Selection(img.pageId, juror.getId, round.getId, rate)

  def apply(img: Image, juror: User, round: Round): Selection = apply(img, juror, round, 0)

  def apply(imagePage: Page, juror: User, round: Round, rate: Int): Selection =
    Selection(imagePage.id.get, juror.getId, round.getId, rate)

  def apply(imagePage: Page, juror: User, round: Round): Selection = apply(imagePage, juror, round)
}
