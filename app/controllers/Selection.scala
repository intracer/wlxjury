package controllers

import scalikejdbc._, scalikejdbc.SQLInterpolation._
import org.joda.time.DateTime

case class Selection(
                      id: Long,
                      filename: String,
                     	fileid: String,
                      juryid: Int,
                      email: String,
                      round: Int,
                      createdAt: DateTime,
                      deletedAt: Option[DateTime] = None)
{

  def destroy()(implicit session: DBSession = Selection.autoSession): Unit = Selection.destroy(filename, email)(session)

}


object Selection extends SQLSyntaxSupport[Selection]{


  def apply(c: SyntaxProvider[Selection])(rs: WrappedResultSet): Selection = apply(c.resultName)(rs)
  def apply(c: ResultName[Selection])(rs: WrappedResultSet): Selection = new Selection(
    id = rs.int(c.id),
    filename = rs.string(c.filename),
    fileid = rs.string(c.fileid),
    juryid = rs.int(c.juryid),
    email = rs.string(c.email),
    round = rs.int(c.round),
    createdAt = rs.timestamp(c.createdAt).toJodaDateTime,
    deletedAt = rs.timestampOpt(c.deletedAt).map(_.toJodaDateTime)
  )

  val c = Selection.syntax("c")
  // val autoSession = AutoSession
  private val isNotDeleted = sqls.isNull(c.deletedAt)

  def find(id: Long)(implicit session: DBSession = autoSession): Option[Selection] = withSQL {
    select.from(Selection as c).where.eq(c.id, id).and.append(isNotDeleted)
  }.map(Selection(c)).single.apply()

  def findAll()(implicit session: DBSession = autoSession): List[Selection] = withSQL {
    select.from(Selection as c)
      .where.append(isNotDeleted)
      .orderBy(c.id)
  }.map(Selection(c)).list.apply()

  def countAll()(implicit session: DBSession = autoSession): Long = withSQL {
    select(sqls.count).from(Selection as c).where.append(isNotDeleted)
  }.map(rs => rs.long(1)).single.apply().get

  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[Selection] = withSQL {
    select.from(Selection as c)
      .where.append(isNotDeleted).and.append(sqls"${where}")
      .orderBy(c.id)
  }.map(Selection(c)).list.apply()

  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = withSQL {
    select(sqls.count).from(Selection as c).where.append(isNotDeleted).and.append(sqls"${where}")
  }.map(_.long(1)).single.apply().get

  def create(filename: String, fileid: String, juryid: Int, email: String, round: Int, createdAt: DateTime = DateTime.now)(implicit session: DBSession = autoSession): Selection = {
    val id = withSQL {
      insert.into(Selection).namedValues(
        column.filename -> filename,
        column.fileid -> fileid,
        column.juryid -> juryid,
        column.email -> email.trim.toLowerCase,
        column.round -> round,
        column.createdAt -> createdAt)
    }.updateAndReturnGeneratedKey.apply()

    Selection(id = id, filename = filename, fileid = fileid, juryid = juryid, email = email, round = round, createdAt = createdAt)
  }

  def destroy(filename: String, email: String)(implicit session: DBSession = autoSession): Unit = withSQL {
    update(Selection).set(column.deletedAt -> DateTime.now).where.eq(column.filename, filename).and.eq(column.email, email)
  }.update.apply()

  def destroyAll(filename: String)(implicit session: DBSession = autoSession): Unit = withSQL {
    update(Selection).set(column.deletedAt -> DateTime.now).where.eq(column.filename, filename)
  }.update.apply()



}
