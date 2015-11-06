package org.intracer.wmua

import org.joda.time.DateTime
import scalikejdbc._
import scalikejdbc.interpolation.SQLSyntax._

case class Selection(
                      id: Long,
                      pageId: Long,
                      var rate: Int,
                     //	fileid: String, // TODO remove
                      juryId: Long,
                      round: Long,
                      createdAt: DateTime = DateTime.now,
                      deletedAt: Option[DateTime] = None,
                      criteriaId: Option[Int] = None)
{

  def destroy()(implicit session: DBSession = Selection.autoSession): Unit = Selection.destroy(pageId, juryId, round)(session)

}


object Selection extends SQLSyntaxSupport[Selection]{

  def byUser(user: User, roundId: Long)(implicit session: DBSession = autoSession): Seq[Selection] = withSQL {
    select.from(Selection as s).where
      .eq(s.juryId, user.id).and
      .eq(s.round, roundId).and
      .append(isNotDeleted)
  }.map(Selection(s)).list().apply()

  def byUserSelected(user: User, roundId: Long)(implicit session: DBSession = autoSession): Seq[Selection] = withSQL {
    select.from(Selection as s).where
      .eq(s.juryId, user.id).and
      .eq(s.round, roundId).and
      .ne(s.rate, 0).and
      .append(isNotDeleted)
  }.map(Selection(s)).list().apply()

  def byRoundSelected(roundId: Long)(implicit session: DBSession = autoSession): Seq[Selection] = withSQL {
    select.from(Selection as s).where
      .eq(s.round, roundId).and
      .ne(s.rate, 0).and
      .append(isNotDeleted)
  }.map(Selection(s)).list().apply()

  def byRoundAndImageWithJury(roundId: Long, imageId: Long)(implicit session: DBSession = autoSession): Seq[(Selection, User)] = withSQL {
    select.from(Selection as s)
      .innerJoin(User as User.u).on(User.u.id, Selection.s.juryId)
      .where.eq(s.round, roundId).and
      .eq(s.pageId, imageId).and
      .gt(s.rate, 0).and
      .append(isNotDeleted)
      .orderBy(s.rate).desc
  }.map(rs => (Selection(Selection.s)(rs), User(User.u)(rs))).list().apply()

  def byRoundImageAndJury(roundId: Long, juryId: Long, imageId: Long)(implicit session: DBSession = autoSession): Seq[Selection] = withSQL {
    select.from(Selection as s)
      .where.eq(s.round, roundId).and
      .eq(s.juryId, juryId).and
      .eq(s.pageId, imageId).and
      .gt(s.rate, 0).and
      .append(isNotDeleted)
      .orderBy(s.rate).desc
  }.map(rs => Selection(Selection.s)(rs)).list().apply()

  def byRound(roundId: Long)(implicit session: DBSession = autoSession): Seq[Selection] = withSQL {
    select.from(Selection as s).where
      .eq(s.round, roundId).and
      .append(isNotDeleted)
  }.map(Selection(s)).list().apply()

  def byRoundWithCriteria(roundId: Long)(implicit session: DBSession = autoSession): Seq[Selection] = withSQL {
    select(sum(CriteriaRate.c.rate), count(CriteriaRate.c.rate), s.result.*).from(Selection as s)
      .leftJoin(CriteriaRate as CriteriaRate.c).on(s.id, CriteriaRate.c.selection)
      .where
      .eq(s.round, roundId).and
      .append(isNotDeleted).
       groupBy(s.id)
  }.map(rs => (Selection(s)(rs), rs.int(1), rs.int(2))).list().apply().map {
    case (selection, sum, criterias) => if (criterias > 0) selection.copy(rate = sum / criterias) else selection
  }


    //  select(sum(Selection.s.rate), count(Selection.s.rate), c.result.*).from(ImageJdbc as c)
//    .innerJoin(Selection as Selection.s).on(c.pageId, Selection.s.pageId)
//    .where.eq(Selection.s.round, roundId)
//    .and.gt(Selection.s.rate, 0)
//    .and.append(isNotDeleted).groupBy(Selection.s.pageId)
//}.map(rs => (ImageJdbc(c)(rs), rs.int(1), rs.int(2))).list().apply().map {
//case (i, sum, count) => ImageWithRating(i, Seq(new Selection(0, i.pageId, sum, 0, roundId)), count)


  def byUserNotSelected(user: User, roundId: Long)(implicit session: DBSession = autoSession): Seq[Selection] = withSQL {
    select.from(Selection as s).where
      .eq(s.juryId, user.id).and
      .eq(s.round, roundId).and
      .eq(s.rate, 0).and
      .append(isNotDeleted)
  }.map(Selection(s)).list().apply()


  def apply(c: SyntaxProvider[Selection])(rs: WrappedResultSet): Selection = apply(c.resultName)(rs)
  def apply(c: ResultName[Selection])(rs: WrappedResultSet): Selection = new Selection(
    id = rs.long(c.id),
    pageId = rs.long(c.pageId),
    rate = rs.int(c.rate),
//    fileid = rs.string(c.fileid),
    juryId = rs.long(c.juryId),
    round = rs.long(c.round),
    createdAt = rs.timestamp(c.createdAt).toJodaDateTime,
    deletedAt = rs.timestampOpt(c.deletedAt).map(_.toJodaDateTime),
    criteriaId = rs.intOpt(c.criteriaId)
  )

  val s = Selection.syntax("s")
  // val autoSession = AutoSession
  private val isNotDeleted = sqls.isNull(s.deletedAt)

  def find(id: Long)(implicit session: DBSession = autoSession): Option[Selection] = withSQL {
    select.from(Selection as s).where.eq(s.id, id).and.append(isNotDeleted)
  }.map(Selection(s)).single().apply()

  def findBy(pageId: Long, juryId: Long, round: Long)(implicit session: DBSession = autoSession): Option[Selection] = withSQL {
    select.from(Selection as s).where
      .eq(s.pageId, pageId).and
      .eq(s.juryId, juryId).and
      .eq(s.round, round).and
      .append(isNotDeleted)
  }.map(Selection(s)).single().apply()

  def findAll()(implicit session: DBSession = autoSession): List[Selection] = withSQL {
    select.from(Selection as s)
      .where.append(isNotDeleted)
      .orderBy(s.id)
  }.map(Selection(s)).list().apply()

  def countAll()(implicit session: DBSession = autoSession): Long = withSQL {
    select(sqls.count).from(Selection as s).where.append(isNotDeleted)
  }.map(rs => rs.long(1)).single().apply().get

  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[Selection] = withSQL {
    select.from(Selection as s)
      .where.append(isNotDeleted).and.append(sqls"$where")
      .orderBy(s.id)
  }.map(Selection(s)).list().apply()

  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = withSQL {
    select(sqls.count).from(Selection as s).where.append(isNotDeleted).and.append(sqls"$where")
  }.map(_.long(1)).single().apply().get

  def create(pageId: Long, rate: Int,
             fileid: String, juryId: Int, round: Int, createdAt: DateTime = DateTime.now)(implicit session: DBSession = autoSession): Selection = {
    val id = withSQL {
      insert.into(Selection).namedValues(
        column.pageId -> pageId,
        column.rate -> rate,
        column.juryId -> juryId,
        column.round -> round,
        column.createdAt -> createdAt
//,        column.criteria_id -> criteria
      )
    }.updateAndReturnGeneratedKey().apply()

     Selection(id = id, pageId = pageId, rate = rate, juryId = juryId, round = round, createdAt = createdAt)
  }

  def batchInsert(selections: Seq[Selection]) {
    val column = Selection.column
    DB localTx { implicit session =>
      val batchParams: Seq[Seq[Any]] = selections.map(i => Seq(
        i.pageId,
        i.rate,
//        i.fileid,
        i.juryId,
        i.round,
        i.createdAt,
        i.criteriaId))
      withSQL {
        insert.into(Selection).namedValues(
          column.pageId -> sqls.?,
          column.rate -> sqls.?,
//          column.fileid -> sqls.?,
          column.juryId -> sqls.?,
          column.round -> sqls.?,
          column.createdAt -> sqls.?,
          column.criteriaId -> sqls.?
        )
      }.batch(batchParams: _*).apply()
    }
  }

  def destroy(pageId: Long, juryId: Long, round: Long)(implicit session: DBSession = autoSession): Unit = withSQL {
    update(Selection).set(column.rate -> -1).where.
      eq(column.pageId, pageId).and.
      eq(column.juryId, juryId).and.
      eq(column.round, round)
  }.update().apply()

  def rate(pageId: Long, juryId: Long, round: Long, rate: Int = 1)(implicit session: DBSession = autoSession): Unit = withSQL {
    update(Selection).set(column.rate -> rate).where.
      eq(column.pageId, pageId).and.
      eq(column.juryId, juryId).and.
      eq(column.round, round)
  }.update().apply()

  def rateWithCriteria(pageId: Long, juryId: Long, round: Long, rate: Int = 1, criteriaId: Int)(implicit session: DBSession = autoSession): Unit = withSQL {
    update(Selection).set(column.rate -> rate).where.
      eq(column.pageId, pageId).and.
      eq(column.juryId, juryId).and.
      eq(column.round, round).and.
      eq(column.criteriaId, criteriaId)
  }.update().apply()

//  def rateFromCriteria(pageId: Long, juryId: Long, round: Long )(implicit session: DBSession = autoSession): Unit = withSQL {
//    update(Selection).set(column.rate -> rate).where.
//      eq(column.pageId, pageId).and.
//      eq(column.juryId, juryId).and.
//      eq(column.round, round).and.
//      eq(column.criteriaId, criteriaId)
//  }.update().apply()
//

  import SQLSyntax.{count, distinct}
  def activeJurors(roundId: Long)(implicit session: DBSession = autoSession): Int =
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
  //      select(s.juryId).from(Selection as s).where.eq(s.round, roundId).as(j)
  //        .groupBy(j.juryId)
  //        .having(gt(sum(j.rate), 0))
  //    }
  //    //      .append(isNotDeleted)


  def allJurors(roundId: Long)(implicit session: DBSession = autoSession): Int = withSQL {
    select(count(distinct(Selection.s.juryId))).from(Selection as Selection.s).where.eq(Selection.s.round, roundId).and.append(isNotDeleted)
  }.map(_.int(1)).single().apply().get


   def destroyAll(pageId: Long)(implicit session: DBSession = autoSession): Unit = withSQL {
    update(Selection).set(column.deletedAt -> DateTime.now).where.eq(column.pageId, pageId)
  }.update.apply()

}
