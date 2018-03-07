package org.intracer.wmua

import java.time.ZonedDateTime

import db.scalikejdbc.SelectionJdbc

case class Selection(
                      id: Long,
                      pageId: Long,
                      var rate: Int,
                      juryId: Long,
                      round: Long,
                      createdAt: ZonedDateTime = ZonedDateTime.now,
                      deletedAt: Option[ZonedDateTime] = None,
                      criteriaId: Option[Int] = None
                      )
{

  def destroy() = SelectionJdbc.destroy(pageId, juryId, round)

}


