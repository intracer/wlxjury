package db.scalikejdbc

import db.SelectionDao
import org.intracer.wmua.{Selection, User}
import org.joda.time.DateTime
import scalikejdbc._

object SelectionJdbc extends SQLSyntaxSupport[Selection] with SelectionDao {

  implicit def session: DBSession = autoSession

  override val tableName = "selection"

  def byUser(user: User, roundId: Long): Seq[Selection] = withSQL {
    select.from(SelectionJdbc as s).where
      .eq(s.juryId, user.id).and
      .eq(s.round, roundId).and
      .append(isNotDeleted)
  }.map(SelectionJdbc(s)).list().apply()

  def byUserSelected(user: User, roundId: Long): Seq[Selection] = withSQL {
    select.from(SelectionJdbc as s).where
      .eq(s.juryId, user.id).and
      .eq(s.round, roundId).and
      .ne(s.rate, 0).and
      .append(isNotDeleted)
  }.map(SelectionJdbc(s)).list().apply()

  def byRoundSelected(roundId: Long): Seq[Selection] = withSQL {
    select.from(SelectionJdbc as s).where
      .eq(s.round, roundId).and
      .ne(s.rate, 0).and
      .append(isNotDeleted)
  }.map(SelectionJdbc(s)).list().apply()

  def byRoundAndImageWithJury(roundId: Long, imageId: Long): Seq[(Selection, User)] = withSQL {
    select.from(SelectionJdbc as s)
      .innerJoin(UserJdbc as UserJdbc.u).on(UserJdbc.u.id, SelectionJdbc.s.juryId)
      .where.eq(s.round, roundId).and
      .eq(s.pageId, imageId).and
      .gt(s.rate, 0).and
      .append(isNotDeleted)
      .orderBy(s.rate).desc
  }.map(rs => (SelectionJdbc(SelectionJdbc.s)(rs), UserJdbc(UserJdbc.u)(rs))).list().apply()

  def byRound(roundId: Long): Seq[Selection] = withSQL {
    select.from(SelectionJdbc as s).where
      .eq(s.round, roundId).and
      .append(isNotDeleted)
  }.map(SelectionJdbc(s)).list().apply()

  def byUserNotSelected(user: User, roundId: Long): Seq[Selection] = withSQL {
    select.from(SelectionJdbc as s).where
      .eq(s.juryId, user.id).and
      .eq(s.round, roundId).and
      .eq(s.rate, 0).and
      .append(isNotDeleted)
  }.map(SelectionJdbc(s)).list().apply()

  def apply(c: SyntaxProvider[Selection])(rs: WrappedResultSet): Selection = apply(c.resultName)(rs)

  def apply(c: ResultName[Selection])(rs: WrappedResultSet): Selection = new Selection(
    id = rs.int(c.id),
    pageId = rs.long(c.pageId),
    rate = rs.int(c.rate),
    //    fileid = rs.string(c.fileid),
    juryId = rs.long(c.juryId),
    round = rs.long(c.round),
    createdAt = rs.timestamp(c.createdAt).toJodaDateTime,
    deletedAt = rs.timestampOpt(c.deletedAt).map(_.toJodaDateTime)
  )

  val s = SelectionJdbc.syntax("s")
  // val autoSession = AutoSession
  private val isNotDeleted = sqls.isNull(s.deletedAt)

  def find(id: Long): Option[Selection] = withSQL {
    select.from(SelectionJdbc as s).where.eq(s.id, id).and.append(isNotDeleted)
  }.map(SelectionJdbc(s)).single().apply()

  def findAll(): List[Selection] = withSQL {
    select.from(SelectionJdbc as s)
      .where.append(isNotDeleted)
      .orderBy(s.id)
  }.map(SelectionJdbc(s)).list().apply()

  def countAll(): Long = withSQL {
    select(sqls.count).from(SelectionJdbc as s).where.append(isNotDeleted)
  }.map(rs => rs.long(1)).single().apply().get

  def findAllBy(where: SQLSyntax): List[Selection] = withSQL {
    select.from(SelectionJdbc as s)
      .where.append(isNotDeleted).and.append(sqls"$where")
      .orderBy(s.id)
  }.map(SelectionJdbc(s)).list().apply()

  def countBy(where: SQLSyntax): Long = withSQL {
    select(sqls.count).from(SelectionJdbc as s).where.append(isNotDeleted).and.append(sqls"$where")
  }.map(_.long(1)).single().apply().get

  def create(pageId: Long, rate: Int,
             fileid: String, juryId: Int, round: Int, createdAt: DateTime = DateTime.now): Selection = {
    val id = withSQL {
      insert.into(SelectionJdbc).namedValues(
        column.pageId -> pageId,
        column.rate -> rate,
        column.juryId -> juryId,
        column.round -> round,
        column.createdAt -> createdAt)
    }.updateAndReturnGeneratedKey().apply()

    Selection(id = id, pageId = pageId, rate = rate, juryId = juryId, round = round, createdAt = createdAt)
  }

  def batchInsert(selections: Seq[Selection]) {
    val column = SelectionJdbc.column
    DB localTx { implicit session =>
      val batchParams: Seq[Seq[Any]] = selections.map(i => Seq(
        i.pageId,
        i.rate,
        //        i.fileid,
        i.juryId,
        i.round,
        i.createdAt))
      withSQL {
        insert.into(SelectionJdbc).namedValues(
          column.pageId -> sqls.?,
          column.rate -> sqls.?,
          //          column.fileid -> sqls.?,
          column.juryId -> sqls.?,
          column.round -> sqls.?,
          column.createdAt -> sqls.?
        )
      }.batch(batchParams: _*).apply()
    }
  }

  def destroy(pageId: Long, juryId: Long, round: Long): Unit = withSQL {
    update(SelectionJdbc).set(column.rate -> -1).where.
      eq(column.pageId, pageId).and.
      eq(column.juryId, juryId).and.
      eq(column.round, round)
  }.update().apply()

  def rate(pageId: Long, juryId: Long, round: Long, rate: Int = 1): Unit = withSQL {
    update(SelectionJdbc).set(column.rate -> rate).where.
      eq(column.pageId, pageId).and.
      eq(column.juryId, juryId).and.
      eq(column.round, round)
  }.update().apply()

  import SQLSyntax.{count, distinct}

  def activeJurors(roundId: Long): Int =
    sql"""SELECT count( 1 )
    FROM (

      SELECT s.jury_id
    FROM selection s
    WHERE s.round = $roundId and s.deleted_at is null
    GROUP BY s.jury_id
    HAVING sum(s.rate) > 0
    ) j"""
      .map(_.int(1)).single().apply().get

  //    val j = SubQuery.syntax("j").include(s)
  //    select(count(distinct(j.juryId))).from {
  //      select(s.juryId).from(SelectionJdbc as s).where.eq(s.round, roundId).as(j)
  //        .groupBy(j.juryId)
  //        .having(gt(sum(j.rate), 0))
  //    }
  //    //      .append(isNotDeleted)


  def allJurors(roundId: Long): Int = withSQL {
    select(count(distinct(SelectionJdbc.s.juryId))).from(SelectionJdbc as SelectionJdbc.s).where.eq(SelectionJdbc.s.round, roundId).and.append(isNotDeleted)
  }.map(_.int(1)).single().apply().get


  //  def destroyAll(filename: String): Unit = withSQL {
  //    update(Selection).set(column.deletedAt -> DateTime.now).where.eq(column.filename, filename)
  //  }.update.apply()

}
