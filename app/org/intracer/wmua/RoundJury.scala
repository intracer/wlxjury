package org.intracer.wmua

import scalikejdbc._


case class RoundJury(round: Long, jury: Long) {

}

object RoundJury extends SQLSyntaxSupport[RoundJury] {

  override val tableName = "round_jury"
  val c = RoundJury.syntax("c")

  def apply(c: SyntaxProvider[RoundJury])(rs: WrappedResultSet): RoundJury = apply(c.resultName)(rs)

  def apply(c: ResultName[RoundJury])(rs: WrappedResultSet): RoundJury = new RoundJury(
    round = rs.long(c.round),
    jury = rs.long(c.jury)
  )

  def insertRJ(rj: RoundJury)(implicit session: DBSession = autoSession): Unit = {
    withSQL {
      insert.into(RoundJury).namedValues(
        column.round -> rj.round,
        column.jury -> rj.jury)
    }.updateAndReturnGeneratedKey().apply()
  }

}
