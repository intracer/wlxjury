package org.intracer.wmua

import db.scalikejdbc._
import scalikejdbc._
import org.joda.time.DateTime
import org.scalawiki.dto.Page

case class Round(id: Option[Long],
                 number: Int,
                 name: Option[String] = None,
                 contest: Long,
                 roles: Set[String] = Set("jury"),
                 distribution: Int = 0,
                 rates: Rates = Round.binaryRound,
                 limitMin: Option[Int] = None,
                 limitMax: Option[Int] = None,
                 recommended: Option[Int] = None,
                 images: Seq[Page] = Seq.empty,
                 selected: Seq[Page] = Seq.empty,
                 createdAt: DateTime = DateTime.now,
                 deletedAt: Option[DateTime] = None,
                 active: Boolean = false,
                 optionalRate: Boolean = false,
                 juryOrgView: Boolean = false) {

  def jurors = UserJdbc.findAllBy(sqls.in(UserJdbc.u.roles, roles.toSeq).and.eq(UserJdbc.u.contest, contest))

  def activeJurors = if (!optionalRate) _allJurors else _activeJurors

  lazy val _activeJurors = SelectionJdbc.activeJurors(id.get)

  lazy val _allJurors = SelectionJdbc.allJurors(id.get)


  def allImages = ImageJdbc.byRoundMerged(id.get)

  def description: String = name.flatMap(s => if (s.trim.isEmpty) None else Some(s)).fold(number.toString)(s => s)
}

object Round {
  val binaryRound = new Rates(1, "+/-", -1, 1)
  val rateRounds = (3 to 20).map(i => new Rates(i, s"1-$i rating", 1, i))

  val rates = Seq(binaryRound) ++ rateRounds

  val ratesById = rates.groupBy(_.id).mapValues(_.head)

}


case class Rates(id: Int, name: String, minRate: Int = 0, maxRate: Int = 1)

object Rates {

  val map = Map(1 -> "selected", 0 -> "unrated", -1 -> "rejected")

  val pairs: Seq[(Option[Int], String)] = Seq(Some(1) -> "selected", Some(0) -> "unrated", Some(-1) -> "rejected")


}

//case class Limits(min:Int, max:Int, recommended: Option[Int])


//  val binaryRound = new Rates(1, "+/-", -1, 1)
//  val rateRounds = (3 to 20).map(i => new Rates(i, s"1-$i rating", 1, i))
//
//  val rates = Seq(binaryRound) ++ rateRounds
//
//  val ratesById = rates.groupBy(_.id)
//
//  override val tableName = "rounds"
//
//  def activeRounds(contestId: Int) = Seq(find(ContestJury.currentRound(contestId)).get)
//
//  def current(user: User) = {
//    val contest = ContestJury.byId(user.contest).get
//    Round.find(contest.currentRound).getOrElse(new Round(0, 0, None, 14, Set("jury"), 1, binaryRound, Some(1), Some(1), None))
//  }
//
//  def applyEdit(id: Long, num: Int, name: Option[String], contest: Int, roles: String, distribution: Int,
//                rates: Int, limitMin: Option[Int], limitMax: Option[Int], recommended: Option[Int]) =
//    new Round(id, num, name, contest, Set(roles), distribution, ratesById(rates).head, limitMin, limitMax, recommended)
//
//  def unapplyEdit(round: Round): Option[(Long, Int, Option[String], Int, String, Int, Int, Option[Int], Option[Int], Option[Int])] = {
//    Some((round.id, round.number, round.name, round.contest, round.roles.head, round.distribution, round.rates.id, round.limitMin, round.limitMax, round.recommended))
//  }
//
//  val c = Round.syntax("c")
//
//  def apply(c: SyntaxProvider[Round])(rs: WrappedResultSet): Round = apply(c.resultName)(rs)
//
//  def apply(c: ResultName[Round])(rs: WrappedResultSet): Round = new Round(
//    id = rs.int(c.id),
//    name = Option(rs.string(c.name)),
//    number = rs.int(c.number),
//    distribution = rs.int(c.distribution),
//    contest = rs.int(c.contest),
//    rates = ratesById(rs.int(c.rates)).head,
//    limitMin = rs.intOpt(c.limitMin),
//    limitMax = rs.intOpt(c.limitMax),
//    recommended = rs.intOpt(c.recommended),
//    createdAt = rs.timestamp(c.createdAt).toJodaDateTime,
//    deletedAt = rs.timestampOpt(c.deletedAt).map(_.toJodaDateTime),
//    active = rs.booleanOpt(c.active).getOrElse(false),
//    optionalRate  = rs.booleanOpt(c.optionalRate).getOrElse(false),
//    juryOrgView = rs.booleanOpt(c.juryOrgView).getOrElse(false)
//  )
//
//
//  //  private val autoSession = AutoSession
//  private val isNotDeleted = sqls.isNull(c.deletedAt)
//
//  def findAll()(implicit session: DBSession = autoSession): List[Round] = withSQL {
//    select.from(Round as c)
//      .where.append(isNotDeleted)
//      .orderBy(c.id)
//  }.map(Round(c)).list().apply()
//
//  def findByContest(contest: Long)(implicit session: DBSession = autoSession): List[Round] = withSQL {
//    select.from(Round as c)
//      .where.append(isNotDeleted).and.eq(c.contest, contest)
//      .orderBy(c.id)
//  }.map(Round(c)).list().apply()
//
//  def find(id: Long)(implicit session: DBSession = autoSession): Option[Round] = withSQL {
//    select.from(Round as c).where.eq(c.id, id).and.append(isNotDeleted)
//  }.map(Round(c)).single().apply()
//
//  def create(number: Int, name: Option[String], contest: Int, roles: String, distribution: Int,
//             rates: Int, limitMin: Option[Int], limitMax: Option[Int], recommended: Option[Int],
//             createdAt: DateTime = DateTime.now)(implicit session: DBSession = autoSession): Round = {
//    val id = withSQL {
//      insert.into(Round).namedValues(
//        column.number -> number,
//        column.name -> name,
//        column.contest -> contest,
//        column.roles -> roles,
//        column.distribution -> distribution,
//        column.rates -> rates,
//        column.limitMin -> limitMin,
//        column.limitMax -> limitMax,
//        column.recommended -> recommended,
//        column.createdAt -> createdAt)
//    }.updateAndReturnGeneratedKey().apply()
//
//    new Round(id = id, name = name, number = number, contest = contest, roles = Set(roles), distribution = distribution,
//      rates = ratesById(rates).head, limitMin = limitMin,
//      limitMax = limitMax, recommended = recommended, createdAt = createdAt)
//  }
//
//  def updateRound(id: Long, round: Round)(implicit session: DBSession = autoSession): Unit = withSQL {
//    update(Round).set(
//      column.name -> round.name,
//      column.roles -> round.roles,
//      column.distribution -> round.distribution,
//      column.rates -> round.rates.id,
//      column.limitMin -> round.limitMin,
//      column.limitMax -> round.limitMax,
//      column.recommended -> round.recommended,
//      column.active -> round.active
//    ).where.eq(column.id, id)
//  }.update().apply()
//
//  def countByContest(contest: Int)(implicit session: DBSession = autoSession): Int = withSQL {
//    select(sqls.count).from(Round as c).where.eq(column.contest, contest)
//  }.map(rs => rs.int(1)).single().apply().get
//}