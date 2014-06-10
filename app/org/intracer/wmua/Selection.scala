package org.intracer.wmua

import scalikejdbc._
import org.joda.time.DateTime

case class Selection(
                      id: Long,
                      pageId: Long,
                      var rate: Int,
                     //	fileid: String, // TODO remove
                      juryid: Long,
                      round: Long,
                      createdAt: DateTime,
                      deletedAt: Option[DateTime] = None)
{

  def destroy()(implicit session: DBSession = Selection.autoSession): Unit = Selection.destroy(pageId, juryid, round)(session)

}


object Selection extends SQLSyntaxSupport[Selection]{

  def byUser(user: User, roundId: Long)(implicit session: DBSession = autoSession): Seq[Selection] = withSQL {
    select.from(Selection as s).where
      .eq(s.juryid, user.id).and
      .eq(s.round, roundId).and
      .append(isNotDeleted)
  }.map(Selection(s)).list.apply()

  def byUserSelected(user: User, roundId: Long)(implicit session: DBSession = autoSession): Seq[Selection] = withSQL {
    select.from(Selection as s).where
      .eq(s.juryid, user.id).and
      .eq(s.round, roundId).and
      .ne(s.rate, 0).and
      .append(isNotDeleted)
  }.map(Selection(s)).list.apply()

  def byRoundSelected(roundId: Long)(implicit session: DBSession = autoSession): Seq[Selection] = withSQL {
    select.from(Selection as s).where
      .eq(s.round, roundId).and
      .ne(s.rate, 0).and
      .append(isNotDeleted)
  }.map(Selection(s)).list.apply()

  def byUserNotSelected(user: User, roundId: Long)(implicit session: DBSession = autoSession): Seq[Selection] = withSQL {
    select.from(Selection as s).where
      .eq(s.juryid, user.id).and
      .eq(s.round, roundId).and
      .eq(s.rate, 0).and
      .append(isNotDeleted)
  }.map(Selection(s)).list.apply()


  def apply(c: SyntaxProvider[Selection])(rs: WrappedResultSet): Selection = apply(c.resultName)(rs)
  def apply(c: ResultName[Selection])(rs: WrappedResultSet): Selection = new Selection(
    id = rs.int(c.id),
    pageId = rs.long(c.pageId),
    rate = rs.int(c.rate),
//    fileid = rs.string(c.fileid),
    juryid = rs.long(c.juryid),
    round = rs.long(c.round),
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

  def create(pageId: Long, rate: Int,
             fileid: String, juryid: Int, round: Int, createdAt: DateTime = DateTime.now)(implicit session: DBSession = autoSession): Selection = {
    val id = withSQL {
      insert.into(Selection).namedValues(
        column.pageId -> pageId,
        column.rate -> rate,
        column.juryid -> juryid,
        column.round -> round,
        column.createdAt -> createdAt)
    }.updateAndReturnGeneratedKey.apply()

     Selection(id = id, pageId = pageId, rate = rate, juryid = juryid, round = round, createdAt = createdAt)
  }

  def batchInsert(selections: Seq[Selection]) {
    val column = Selection.column
    DB localTx { implicit session =>
      val batchParams: Seq[Seq[Any]] = selections.map(i => Seq(
        i.pageId,
        i.rate,
//        i.fileid,
        i.juryid,
        i.round,
        i.createdAt))
      withSQL {
        insert.into(Selection).namedValues(
          column.pageId -> sqls.?,
          column.rate -> sqls.?,
//          column.fileid -> sqls.?,
          column.juryid -> sqls.?,
          column.round -> sqls.?,
          column.createdAt -> sqls.?
        )
      }.batch(batchParams: _*).apply()
    }
  }

  def destroy(pageId: Long, juryid: Long, round: Long)(implicit session: DBSession = autoSession): Unit = withSQL {
    update(Selection).set(column.rate -> -1).where.
      eq(column.pageId, pageId).and.
      eq(column.juryid, juryid).and.
      eq(column.round, round)
  }.update.apply()

  def rate(pageId: Long, juryid: Long, round: Long, rate: Int = 1)(implicit session: DBSession = autoSession): Unit = withSQL {
    update(Selection).set(column.rate -> rate).where.
      eq(column.pageId, pageId).and.
      eq(column.juryid, juryid).and.
      eq(column.round, round)
  }.update.apply()



//  def destroyAll(filename: String)(implicit session: DBSession = autoSession): Unit = withSQL {
//    update(Selection).set(column.deletedAt -> DateTime.now).where.eq(column.filename, filename)
//  }.update.apply()

}
