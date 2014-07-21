package org.intracer.wmua

import scalikejdbc._
import org.joda.time.DateTime
import client.dto.Page

case class Round(id: Long, number: Int, name: Option[String], contest: Int,
                 roles: Set[String] = Set("jury"),
                 distribution: Int,
                 rates: Rates = Round.binaryRound,
                 limitMin: Int,
                 limitMax: Int,
                 recommended: Option[Int],
                 jury: Seq[User] = Seq.empty,
                 images: Seq[Page] = Seq.empty,
                 selected: Seq[Page] = Seq.empty,
                 createdAt: DateTime = DateTime.now,
                 deletedAt: Option[DateTime] = None) {

  def jurors = User.findAllBy(sqls.in(User.c.roles, roles.toSeq).and.eq(User.c.contest, contest))

  def allImages = Image.byRoundMerged(id.toInt)

  def description:String = name.flatMap(s => if (s.trim.isEmpty) None else Some(s)).fold(number.toString)(s => s)
}


case class Rates(id: Int, name: String, minRate: Int = 0, maxRate: Int = 1)

//case class Limits(min:Int, max:Int, recommended: Option[Int])

object Round extends SQLSyntaxSupport[Round] {

  val binaryRound = new Rates(1, "+/-", -1, 1)
  val rateRounds = (3 to 10).map(i => new Rates(i, s"1-$i rating", 1, i))

  val rates = Seq(binaryRound) ++ rateRounds

  val ratesById = rates.groupBy(_.id)

  override val tableName = "rounds"

  def activeRounds(contestId: Int) = Seq(find(Contest.currentRound(contestId)).get)

  def current(user: User) = {
    val contest = Contest.byId(user.contest).get
    Round.find(contest.currentRound).getOrElse(new Round(0, 0, None, 14, Set("jury"), 1, binaryRound, 1, 1, None))
  }

  def applyEdit(id: Long, num: Int, name: Option[String], contest: Int, roles: String, distribution: Int,
                rates: Int, limitMin: Int, limitMax: Int, recommended: Option[Int]) =
    new Round(id, num, name, contest, Set(roles), distribution, ratesById(rates).head, limitMin, limitMax, recommended)

  def unapplyEdit(round: Round): Option[(Long, Int, Option[String], Int, String, Int, Int, Int, Int, Option[Int])] = {
    Some((round.id, round.number, round.name, round.contest, round.roles.head, round.distribution, round.rates.id, round.limitMin, round.limitMax, round.recommended))
  }

  val c = Round.syntax("c")

  def apply(c: SyntaxProvider[Round])(rs: WrappedResultSet): Round = apply(c.resultName)(rs)

  def apply(c: ResultName[Round])(rs: WrappedResultSet): Round = new Round(
    id = rs.int(c.id),
    name = Option(rs.string(c.name)),
    number = rs.int(c.number),
    distribution = rs.int(c.distribution),
    contest = rs.int(c.contest),
    rates = ratesById(rs.int(c.rates)).head,
    limitMin = rs.int(c.limitMin),
    limitMax = rs.int(c.limitMax),
    recommended = rs.intOpt(c.recommended),
    createdAt = rs.timestamp(c.createdAt).toJodaDateTime,
    deletedAt = rs.timestampOpt(c.deletedAt).map(_.toJodaDateTime)
  )


  //  private val autoSession = AutoSession
  private val isNotDeleted = sqls.isNull(c.deletedAt)

  def findAll()(implicit session: DBSession = autoSession): List[Round] = withSQL {
    select.from(Round as c)
      .where.append(isNotDeleted)
      .orderBy(c.id)
  }.map(Round(c)).list.apply()

  def findByContest(contest: Long)(implicit session: DBSession = autoSession): List[Round] = withSQL {
    select.from(Round as c)
      .where.append(isNotDeleted).and.eq(c.contest, contest)
      .orderBy(c.id)
  }.map(Round(c)).list.apply()

  def find(id: Long)(implicit session: DBSession = autoSession): Option[Round] = withSQL {
    select.from(Round as c).where.eq(c.id, id).and.append(isNotDeleted)
  }.map(Round(c)).single.apply()

  def create(number: Int, name: Option[String], contest: Int, roles: String, distribution: Int,
             rates: Int, limitMin: Int, limitMax: Int, recommended: Option[Int],
             createdAt: DateTime = DateTime.now)(implicit session: DBSession = autoSession): Round = {
    val id = withSQL {
      insert.into(Round).namedValues(
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
    }.updateAndReturnGeneratedKey.apply()

    new Round(id = id, name = name, number = number, contest = contest, roles = Set(roles), distribution = distribution,
      rates = ratesById(rates).head, limitMin = limitMin,
      limitMax = limitMax, recommended = recommended, createdAt = createdAt)
  }

  def updateRound(id: Long, round: Round)(implicit session: DBSession = autoSession): Unit = withSQL {
    update(Round).set(
      column.name -> round.name,
      column.roles -> round.roles,
      column.distribution -> round.distribution,
      column.rates -> round.rates.id,
      column.limitMin -> round.limitMin,
      column.limitMax -> round.limitMax,
      column.recommended -> round.recommended
    ).where.eq(column.id, id)
  }.update.apply()

  def countByContest(contest: Int)(implicit session: DBSession = autoSession): Int = withSQL {
    select(sqls.count).from(Round as c).where.eq(column.contest, contest)
  }.map(rs => rs.int(1)).single.apply().get
}