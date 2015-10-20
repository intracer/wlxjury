package db.scalikejdbc

import db.RoundDao
import org.intracer.wmua.{ContestJury, Round, User}
import org.joda.time.DateTime
import scalikejdbc._

object RoundJdbc extends SQLSyntaxSupport[Round] with RoundDao {

  implicit def session: DBSession = autoSession

  override val tableName = "rounds"

  val c = RoundJdbc.syntax("c")

  def apply(c: SyntaxProvider[Round])(rs: WrappedResultSet): Round = apply(c.resultName)(rs)

  def apply(c: ResultName[Round])(rs: WrappedResultSet): Round = new Round(
    id = Some(rs.int(c.id)),
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
    juryOrgView = rs.booleanOpt(c.juryOrgView).getOrElse(false)
  )


  //  private val autoSession = AutoSession
  private val isNotDeleted = sqls.isNull(c.deletedAt)

  override def activeRounds(contestId: Long): Seq[Round] = {
    ContestJuryJdbc.currentRound(contestId).flatMap(find).toSeq
  }

  //ContestJuryJdbc.currentRound(contestId).map { roundId => find(roundId) }

  override def current(user: User): Round = {
    val contest: ContestJury = ContestJuryJdbc.byId(user.contest)
    find(contest.currentRound).getOrElse(new Round(Some(0), 0, None, 14, Set("jury"), 1, Round.binaryRound, Some(1), Some(1), None))
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

  override def create(number: Int, name: Option[String], contest: Long, roles: String, distribution: Int,
             rates: Int, limitMin: Option[Int], limitMax: Option[Int], recommended: Option[Int],
             createdAt: DateTime = DateTime.now): Round = {
    val id = withSQL {
      insert.into(RoundJdbc).namedValues(
        column.number -> number,
        column.name -> name,
        column.contest -> contest,
        column.roles -> roles,
        column.distribution -> distribution,
        column.rates -> rates,
        column.limitMin -> limitMin,
        column.limitMax -> limitMax,
        column.recommended -> recommended,
        column.createdAt -> createdAt)
    }.updateAndReturnGeneratedKey().apply()

    new Round(id = Some(id), name = name, number = number, contest = contest, roles = Set(roles), distribution = distribution,
      rates = Round.ratesById(rates), limitMin = limitMin,
      limitMax = limitMax, recommended = recommended, createdAt = createdAt)
  }

  override def create(round: Round): Round = {
    val id = withSQL {
      insert.into(RoundJdbc).namedValues(
        column.number -> round.number,
        column.name -> round.name,
        column.contest -> round.contest,
        column.roles -> round.roles,
        column.distribution -> round.distribution,
        column.rates -> round.rates.id,
        column.limitMin -> round.limitMin,
        column.limitMax -> round.limitMax,
        column.recommended -> round.recommended,
        column.createdAt -> round.createdAt)
    }.updateAndReturnGeneratedKey().apply()

    round.copy(id = Some(id))
  }


  override def updateRound(id: Long, round: Round): Unit = withSQL {
    update(RoundJdbc).set(
      column.name -> round.name,
      column.roles -> round.roles,
      column.distribution -> round.distribution,
      column.rates -> round.rates.id,
      column.limitMin -> round.limitMin,
      column.limitMax -> round.limitMax,
      column.recommended -> round.recommended,
      column.active -> round.active
    ).where.eq(column.id, id)
  }.update().apply()

  override def countByContest(contest: Long): Int = withSQL {
    select(sqls.count).from(RoundJdbc as c).where.eq(column.contest, contest)
  }.map(rs => rs.int(1)).single().apply().get
}