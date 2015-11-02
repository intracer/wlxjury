package org.intracer.wmua

import scalikejdbc._

case class CriteriaRate(id: Long, selection: Long, criteria: Long, rate: Int) {

}

object CriteriaRate extends SQLSyntaxSupport[CriteriaRate] {

  val c = CriteriaRate.syntax("c")

  def apply(c: SyntaxProvider[CriteriaRate])(rs: WrappedResultSet): CriteriaRate = apply(c.resultName)(rs)
  def apply(c: ResultName[CriteriaRate])(rs: WrappedResultSet): CriteriaRate = new CriteriaRate(
    id = rs.long(c.id),
    selection = rs.long(c.selection),
    criteria = rs.long(c.criteria),
    rate = rs.int(c.rate)
  )

  def updateRate(selection: Long, criteria: Long, rate: Int)(implicit session: DBSession = autoSession): Unit = withSQL {
    update(CriteriaRate).set(column.rate -> rate).where.
      eq(column.selection, selection).and.
      eq(column.criteria, criteria)
  }.update().apply()

  def getRates(selection: Long)(implicit session: DBSession = autoSession): Seq[CriteriaRate] = withSQL {
    select.from(CriteriaRate as c).where.
      eq(column.selection, selection)
  }.map(CriteriaRate(c)).list().apply()

}
