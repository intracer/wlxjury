package db.scalikejdbc

import db.RoundDao
import org.intracer.wmua.{Rates, Round, User}
import org.joda.time.DateTime
import scalikejdbc._

object RoundJdbc extends SQLSyntaxSupport[Round] with RoundDao {

  implicit def session: DBSession = autoSession

  val binaryRound = new Rates(1, "+/-", -1, 1)
  val rateRounds = (3 to 20).map(i => new Rates(i, s"1-$i rating", 1, i))

  val rates = Seq(binaryRound) ++ rateRounds

  val ratesById = rates.groupBy(_.id)

  override val tableName = "rounds"

  def applyEdit(id: Long, num: Int, name: Option[String], contest: Int, roles: String, distribution: Int,
                rates: Int, limitMin: Option[Int], limitMax: Option[Int], recommended: Option[Int]) =
    new Round(id, num, name, contest, Set(roles), distribution, ratesById(rates).head, limitMin, limitMax, recommended)

  def unapplyEdit(round: Round): Option[(Long, Int, Option[String], Int, String, Int, Int, Option[Int], Option[Int], Option[Int])] = {
    Some((round.id, round.number, round.name, round.contest, round.roles.head, round.distribution, round.rates.id, round.limitMin, round.limitMax, round.recommended))
  }

  val c = RoundJdbc.syntax("c")

  def apply(c: SyntaxProvider[Round])(rs: WrappedResultSet): Round = apply(c.resultName)(rs)

  def apply(c: ResultName[Round])(rs: WrappedResultSet): Round = new Round(
    id = rs.int(c.id),
    name = Option(rs.string(c.name)),
    number = rs.int(c.number),
    distribution = rs.int(c.distribution),
    contest = rs.int(c.contest),
    rates = ratesById(rs.int(c.rates)).head,
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

  def activeRounds(contestId: Int) = ContestJuryJdbc.currentRound(contestId).map { roundId => find(roundId) }

  def current(user: User) = {
    ContestJuryJdbc.byId(user.contest).map { contest =>
      find(contest.currentRound).getOrElse(new Round(0, 0, None, 14, Set("jury"), 1, binaryRound, Some(1), Some(1), None))
    }
  }

  def findAll(): List[Round] = withSQL {
    select.from(RoundJdbc as c)
      .where.append(isNotDeleted)
      .orderBy(c.id)
  }.map(RoundJdbc(c)).list().apply()

  def findByContest(contest: Long): List[Round] = withSQL {
    select.from(RoundJdbc as c)
      .where.append(isNotDeleted).and.eq(c.contest, contest)
      .orderBy(c.id)
  }.map(RoundJdbc(c)).list().apply()

  def find(id: Long): Option[Round] = withSQL {
    select.from(RoundJdbc as c).where.eq(c.id, id).and.append(isNotDeleted)
  }.map(RoundJdbc(c)).single().apply()

  def create(number: Int, name: Option[String], contest: Int, roles: String, distribution: Int,
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

    new Round(id = id, name = name, number = number, contest = contest, roles = Set(roles), distribution = distribution,
      rates = ratesById(rates).head, limitMin = limitMin,
      limitMax = limitMax, recommended = recommended, createdAt = createdAt)
  }

  def updateRound(id: Long, round: Round): Unit = withSQL {
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

  def countByContest(contest: Int): Int = withSQL {
    select(sqls.count).from(RoundJdbc as c).where.eq(column.contest, contest)
  }.map(rs => rs.int(1)).single().apply().get
}