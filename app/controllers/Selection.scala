package controllers

import scalikejdbc._, scalikejdbc.SQLInterpolation._
import org.joda.time.DateTime

case class Selection(
                      id: Long,
                      pageId: Long,
                      rate: Int,
                      filename: String,
                     	fileid: String, // TODO remove
                      juryid: Int,
                      round: Int,
                      createdAt: DateTime,
                      deletedAt: Option[DateTime] = None)
{

  def destroy()(implicit session: DBSession = Selection.autoSession): Unit = Selection.destroy(pageId, juryid, round)(session)

}


object Selection extends SQLSyntaxSupport[Selection]{


  def apply(c: SyntaxProvider[Selection])(rs: WrappedResultSet): Selection = apply(c.resultName)(rs)
  def apply(c: ResultName[Selection])(rs: WrappedResultSet): Selection = new Selection(
    id = rs.int(c.id),
    pageId = rs.long(c.pageId),
    rate = rs.int(c.rate),
    filename = rs.string(c.filename),
    fileid = rs.string(c.fileid),
    juryid = rs.int(c.juryid),
    round = rs.int(c.round),
    createdAt = rs.timestamp(c.createdAt).toJodaDateTime,
    deletedAt = rs.timestampOpt(c.deletedAt).map(_.toJodaDateTime)
  )

  val s = Selection.syntax("s")
  // val autoSession = AutoSession
  private val isNotDeleted = sqls.isNull(s.deletedAt)

  def find(id: Long)(implicit session: DBSession = autoSession): Option[Selection] = withSQL {
    select.from(Selection as s).where.eq(s.id, id).and.append(isNotDeleted)
  }.map(Selection(s)).single.apply()

  def findAll()(implicit session: DBSession = autoSession): List[Selection] = withSQL {
    select.from(Selection as s)
      .where.append(isNotDeleted)
      .orderBy(s.id)
  }.map(Selection(s)).list.apply()

  def countAll()(implicit session: DBSession = autoSession): Long = withSQL {
    select(sqls.count).from(Selection as s).where.append(isNotDeleted)
  }.map(rs => rs.long(1)).single.apply().get

  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[Selection] = withSQL {
    select.from(Selection as s)
      .where.append(isNotDeleted).and.append(sqls"${where}")
      .orderBy(s.id)
  }.map(Selection(s)).list.apply()

  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = withSQL {
    select(sqls.count).from(Selection as s).where.append(isNotDeleted).and.append(sqls"${where}")
  }.map(_.long(1)).single.apply().get

  def create(filename: String, pageId: Long, rate: Int,
             fileid: String, juryid: Int, round: Int, createdAt: DateTime = DateTime.now)(implicit session: DBSession = autoSession): Selection = {
    val id = withSQL {
      insert.into(Selection).namedValues(
        column.pageId -> pageId,
        column.rate -> rate,
        column.filename -> filename,
        column.fileid -> fileid,
        column.juryid -> juryid,
//        column.email -> email.trim.toLowerCase,
        column.round -> round,
        column.createdAt -> createdAt)
    }.updateAndReturnGeneratedKey.apply()

    Selection(id = id, pageId = pageId, rate = rate, filename = filename, fileid = fileid, juryid = juryid, round = round, createdAt = createdAt)
  }

  def destroy(pageId: Long, juryid: Long, round: Long)(implicit session: DBSession = autoSession): Unit = withSQL {
    update(Selection).set(column.deletedAt -> DateTime.now).where.
      eq(column.pageId, pageId).and.
      eq(column.juryid, juryid).and.
      eq(column.round, round)
  }.update.apply()

  def destroyAll(filename: String)(implicit session: DBSession = autoSession): Unit = withSQL {
    update(Selection).set(column.deletedAt -> DateTime.now).where.eq(column.filename, filename)
  }.update.apply()



}
