package db.scalikejdbc

import db.RoundDao
import org.intracer.wmua.{ContestJury, Round, User}
import org.joda.time.DateTime
import scalikejdbc._

object RoundJdbc extends SQLSyntaxSupport[Round] with RoundDao {

  implicit def session: DBSession = autoSession

  override val tableName = "rounds"

  val c = RoundJdbc.syntax("c")

  //  private val autoSession = AutoSession
  private def isNotDeleted = sqls.isNull(c.deletedAt)

  def apply(c: SyntaxProvider[Round])(rs: WrappedResultSet): Round = apply(c.resultName)(rs)

  def apply(c: ResultName[Round])(rs: WrappedResultSet): Round = new Round(
    id = Some(rs.long(c.id)),
    name = Option(rs.string(c.name)),
    number = rs.int(c.number),
    distribution = rs.int(c.distribution),
    contest = rs.long(c.contest),
    rates = Round.ratesById(rs.int(c.rates)),
    limitMin = rs.intOpt(c.limitMin),
    limitMax = rs.intOpt(c.limitMax),
    recommended = rs.intOpt(c.recommended),
    createdAt = rs.timestamp(c.createdAt).toJodaDateTime,
    deletedAt = rs.timestampOpt(c.deletedAt).map(_.toJodaDateTime),
    active = rs.booleanOpt(c.active).getOrElse(false),
    optionalRate = rs.booleanOpt(c.optionalRate).getOrElse(false),
    juryOrgView = rs.booleanOpt(c.juryOrgView).getOrElse(false),
    minMpx = rs.intOpt(c.minMpx),
    previous = rs.longOpt(c.previous),
    prevSelectedBy = rs.intOpt(c.prevSelectedBy),
    prevMinAvgRate = rs.intOpt(c.prevMinAvgRate),
    category = rs.stringOpt(c.category),
    categoryClause = rs.intOpt(c.categoryClause),
    regions = rs.stringOpt(c.regions),
    minImageSize = rs.intOpt(c.minImageSize),
    hasCriteria = rs.booleanOpt(c.hasCriteria).getOrElse(false)
  )

  override def create(round: Round): Round = {
    val id = withSQL {
      insert.into(RoundJdbc).namedValues(
        column.number -> round.number,
        column.name -> round.name,
        column.contest -> round.contest,
        column.roles -> round.roles.head,
        column.distribution -> round.distribution,
        column.rates -> round.rates.id,
        column.limitMin -> round.limitMin,
        column.limitMax -> round.limitMax,
        column.recommended -> round.recommended,
        column.createdAt -> round.createdAt,
        column.active -> round.active,
        column.optionalRate -> round.optionalRate,
        column.juryOrgView -> round.juryOrgView,
        column.previous -> round.previous,
        column.prevSelectedBy -> round.prevSelectedBy,
        column.prevMinAvgRate -> round.prevMinAvgRate,
        column.category -> round.category,
        column.categoryClause -> round.categoryClause,
        column.regions -> round.regions,
        column.minImageSize -> round.minImageSize
      )
    }.updateAndReturnGeneratedKey().apply()

    round.copy(id = Some(id))
  }

  override def updateRound(id: Long, round: Round) = withSQL {
    update(RoundJdbc).set(
      column.name -> round.name,
//      column.roles -> round.roles,
//      column.distribution -> round.distribution,
//      column.rates -> round.rates.id,
//      column.limitMin -> round.limitMin,
//      column.limitMax -> round.limitMax,
//      column.recommended -> round.recommended,
      column.active -> round.active
    ).where.eq(column.id, id)
  }.update().apply()

  override def activeRounds(contestId: Long): Seq[Round] = withSQL {
    select.from(RoundJdbc as c)
      .where.append(isNotDeleted).and
      .eq(c.contest, contestId).and
      .eq(c.active, true)
      .orderBy(c.id)
  }.map(RoundJdbc(c)).list().apply()

  override def current(user: User): Option[Round] = {
    for (contest <- user.currentContest.flatMap(ContestJuryJdbc.byId);
         roundId <- contest.currentRound;
         round <- find(roundId))
      yield round
  }

  override def findAll(): List[Round] = withSQL {
    select.from(RoundJdbc as c)
      .where.append(isNotDeleted)
      .orderBy(c.id)
  }.map(RoundJdbc(c)).list().apply()

  override def findByContest(contest: Long): Seq[Round] = withSQL {
    select.from(RoundJdbc as c)
      .where.append(isNotDeleted).and.eq(c.contest, contest)
      .orderBy(c.id)
  }.map(RoundJdbc(c)).list().apply()

  override def find(id: Long): Option[Round] = withSQL {
    select.from(RoundJdbc as c).where.eq(c.id, id).and.append(isNotDeleted)
  }.map(RoundJdbc(c)).single().apply()

  override def setActive(id: Long, active: Boolean): Unit = withSQL {
    update(RoundJdbc).set(
      column.active -> active
    ).where.eq(column.id, id)
  }.update().apply()

  def setInActiveAllInContest(contestId: Long): Unit = withSQL {
    update(RoundJdbc).set(
      column.active -> false
    ).where.eq(column.contest, contestId)
  }.update().apply()

  override def countByContest(contest: Long): Int = withSQL {
    select(sqls.count).from(RoundJdbc as c).where.eq(column.contest, contest)
  }.map(rs => rs.int(1)).single().apply().get

  case class RoundStatRow(juror: Long, rate: Int, count: Int)

  def roundUserStat(roundId: Long): Seq[RoundStatRow] =
    sql"""SELECT u.id, s.rate, count(1) FROM users u
      JOIN selection s ON s.jury_id = u.id
    WHERE s.round = $roundId
    GROUP BY u.id, s.rate""".map(rs =>
      RoundStatRow(rs.int(1), rs.int(2), rs.int(3))
    ).list().apply()

  def roundRateStat(roundId: Long): Seq[(Int, Int)] =
    sql"""SELECT rate, count(1) FROM
(SELECT DISTINCT s.page_id, s.rate FROM users u
  JOIN selection s ON s.jury_id = u.id
  WHERE s.round = $roundId) t
  GROUP BY rate""".map(rs => (rs.int(1), rs.int(2))).list().apply()

}