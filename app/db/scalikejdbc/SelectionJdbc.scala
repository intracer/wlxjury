package db.scalikejdbc

import org.intracer.wmua.{CriteriaRate, Selection, User}
import org.joda.time.DateTime
import scalikejdbc._
import skinny.orm.SkinnyCRUDMapper

object SelectionJdbc extends SkinnyCRUDMapper[Selection] {

  implicit def session: DBSession = autoSession

  override val tableName = "selection"

  val s = SelectionJdbc.syntax("s")
  val u = UserJdbc.u
  val c = CriteriaRate.c

  private def isNotDeleted = sqls.isNull(s.deletedAt)

  override lazy val defaultAlias = createAlias("s")

  override def extract(rs: WrappedResultSet, c: ResultName[Selection]): Selection = Selection(
    id = rs.int(c.id),
    pageId = rs.long(c.pageId),
    rate = rs.int(c.rate),
    juryId = rs.long(c.juryId),
    round = rs.long(c.round),
    createdAt = rs.timestamp(c.createdAt).toJodaDateTime,
    deletedAt = rs.timestampOpt(c.deletedAt).map(_.toJodaDateTime)
  )

  def apply(c: ResultName[Selection])(rs: WrappedResultSet): Selection = Selection(
    id = rs.int(c.id),
    pageId = rs.long(c.pageId),
    rate = rs.int(c.rate),
    juryId = rs.long(c.juryId),
    round = rs.long(c.round),
    createdAt = rs.timestamp(c.createdAt).toJodaDateTime,
    deletedAt = rs.timestampOpt(c.deletedAt).map(_.toJodaDateTime)
  )

  def create(pageId: Long, rate: Int, juryId: Long, roundId: Long, createdAt: DateTime = DateTime.now): Selection = {
    val id = withSQL {
      insert.into(SelectionJdbc).namedValues(
        column.pageId -> pageId,
        column.rate -> rate,
        column.juryId -> juryId,
        column.round -> roundId,
        column.createdAt -> createdAt)
    }.updateAndReturnGeneratedKey().apply()

    Selection(id = id, pageId = pageId, rate = rate, juryId = juryId, round = roundId, createdAt = createdAt)
  }

  def batchInsert(selections: Seq[Selection]) {
    val column = SelectionJdbc.column
    DB localTx { implicit session =>
      val batchParams: Seq[Seq[Any]] = selections.map(i => Seq(
        i.pageId,
        i.rate,
        i.juryId,
        i.round,
        i.createdAt))
      withSQL {
        insert.into(SelectionJdbc).namedValues(
          column.pageId -> sqls.?,
          column.rate -> sqls.?,
          column.juryId -> sqls.?,
          column.round -> sqls.?,
          column.createdAt -> sqls.?
        )
      }.batch(batchParams: _*).apply()
    }
  }

  def byRound(roundId: Long): Seq[Selection] =
    where('round -> roundId).apply()

  def byRoundSelected(roundId: Long): Seq[Selection] =
    where('round -> roundId)
      .where(sqls.ne(s.rate, 0)).apply()

  def byUser(user: User, roundId: Long): Seq[Selection] =
    where('juryId -> user.id, 'round -> roundId).apply()

  def byUserSelected(user: User, roundId: Long): Seq[Selection] =
    where('juryId -> user.id, 'round -> roundId)
      .where(sqls.ne(s.rate, 0)).apply()

  def byUserNotSelected(user: User, roundId: Long): Seq[Selection] =
    where('juryId -> user.id, 'round -> roundId, 'rate -> 0).apply()

  def byRoundAndImageWithJury(roundId: Long, imageId: Long): Seq[(Selection, User)] = withSQL {
    select.from(SelectionJdbc as s)
      .innerJoin(UserJdbc as u).on(u.id, s.juryId)
      .where.eq(s.round, roundId).and
      .eq(s.pageId, imageId).and
      .gt(s.rate, 0).and
      .append(isNotDeleted)
      .orderBy(s.rate).desc
  }.map(rs => (SelectionJdbc(s)(rs), UserJdbc(u)(rs))).list().apply()

  def findBy(pageId: Long, juryId: Long, roundId: Long): Option[Selection] =
    where('pageId -> pageId, 'juryId -> juryId, 'round -> roundId)
      .apply().headOption

  def byRoundWithCriteria(roundId: Long): Seq[Selection] = withSQL {
    select(SQLSyntax.sum(c.rate), SQLSyntax.count(c.rate), s.result.*).from(SelectionJdbc as s)
      .leftJoin(CriteriaRate as c).on(s.id, c.selection)
      .where
      .eq(s.round, roundId).and
      .append(isNotDeleted).
      groupBy(s.id)
  }.map(rs => (SelectionJdbc(s)(rs), rs.intOpt(1).getOrElse(0), rs.intOpt(2).getOrElse(0))).list().apply().map {
    case (selection, sum, criterias) => if (criterias > 0) selection.copy(rate = sum / criterias) else selection
  }

  def destroy(pageId: Long, juryId: Long, round: Long): Unit =
    updateBy(sqls
      .eq(s.pageId, pageId).and.
      eq(s.juryId, juryId).and.
      eq(s.round, round)
    ).withAttributes('rate -> -1)

  def rate(pageId: Long, juryId: Long, round: Long, rate: Int = 1): Unit = withSQL {
    update(SelectionJdbc).set(column.rate -> rate).where.
      eq(column.pageId, pageId).and.
      eq(column.juryId, juryId).and.
      eq(column.round, round)
  }.update().apply()

  def setRound(pageId: Long, oldRound: Long, newRound: Long ): Unit = withSQL {
    update(SelectionJdbc).set(column.round -> newRound).where.
      eq(column.pageId, pageId).and.
      eq(column.round, oldRound)
  }.update().apply()

  def activeJurors(roundId: Long): Int =
    sql"""SELECT count( 1 )
    FROM (

      SELECT s.jury_id
    FROM selection s
    WHERE s.round = $roundId AND s.deleted_at IS NULL
    GROUP BY s.jury_id
    HAVING sum(s.rate) > 0
    ) j"""
      .map(_.int(1)).single().apply().get

  def allJurors(roundId: Long): Long =
    where('round -> roundId)
      .distinctCount('juryId)

  def destroyAll(pageId: Long): Unit =
    updateBy(sqls.eq(s.pageId, pageId))
      .withAttributes('deletedAt -> DateTime.now)

  def removeImage(pageId: Long, roundId: Long): Unit =
    deleteBy(sqls
      .eq(s.pageId, pageId).and
      .eq(s.round, roundId)
    )
}
