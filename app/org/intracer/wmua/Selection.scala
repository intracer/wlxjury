package org.intracer.wmua

import db.scalikejdbc.SelectionJdbc
import org.joda.time.DateTime

case class Selection(
                      id: Long,
                      pageId: Long,
                      var rate: Int,
                      juryId: Long,
                      round: Long,
                      createdAt: DateTime = DateTime.now,
                      deletedAt: Option[DateTime] = None,
                      criteriaId: Option[Int] = None)
{

  def destroy() = SelectionJdbc.destroy(pageId, juryId, round)

}


