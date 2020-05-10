package db.scalikejdbc

import java.time.ZonedDateTime

import org.intracer.wmua.{HasId, ImageWithRating}
import org.scalawiki.dto.Page
import scalikejdbc._
import skinny.orm.SkinnyCRUDMapper

case class Round(id: Option[Long],
                 number: Long,
                 name: Option[String] = None,
                 contestId: Long,
                 roles: Set[String] = Set("jury"),
                 distribution: Int = 0,
                 rates: Rates = Round.binaryRound,
                 limitMin: Option[Int] = None,
                 limitMax: Option[Int] = None,
                 recommended: Option[Int] = None,
                 images: Seq[Page] = Seq.empty,
                 selected: Seq[Page] = Seq.empty,
                 createdAt: ZonedDateTime = ZonedDateTime.now,
                 deletedAt: Option[ZonedDateTime] = None,
                 active: Boolean = false,
                 optionalRate: Boolean = false,
                 juryOrgView: Boolean = false,
                 minMpx: Option[Int] = None,
                 previous: Option[Long] = None,
                 prevSelectedBy: Option[Int] = None,
                 prevMinAvgRate: Option[Int] = None,
                 category: Option[String] = None,
                 excludeCategory: Option[String] = None,
                 categoryClause: Option[Int] = None,
                 regions: Option[String] = None,
                 minImageSize: Option[Int] = None,
                 hasCriteria: Boolean = false,
                 halfStar: Option[Boolean] = None,
                 monuments: Option[String] = None,
                 topImages: Option[Int] = None,
                 specialNomination: Option[String] = None
                ) extends HasId {

  def jurors: Seq[User] =
    User.findAllBy(sqls.in(User.u.roles, roles.toSeq).and.eq(User.u.contestId, contestId))

  def activeJurors: Long = if (!optionalRate) _allJurors else _activeJurors

  lazy val _activeJurors = SelectionJdbc.activeJurors(id.get)

  lazy val _allJurors = SelectionJdbc.allJurors(id.get)

  def allImages: Seq[ImageWithRating] = ImageJdbc.byRoundMerged(id.get)

  def description: String = name
    .filter(_.trim.nonEmpty)
    .getOrElse(number.toString)

  def isBinary: Boolean = rates.id == Round.binaryRound.id

  def regionIds: Seq[String] = regions.map(_.split(",").toSeq).getOrElse(Nil)

  def monumentIds: Seq[String] = monuments.map(_.split(",").toSeq).getOrElse(Nil)

  def fixCategory(name: String): String = {
    if (!name.toLowerCase().startsWith("category:")) {
      "Category:" + name
    } else {
      name
    }
  }

  def withFixedCategories: Round = {
    copy(
      category = category.map(fixCategory),
      excludeCategory = excludeCategory.map(fixCategory)
    )
  }

}

case class Rates(id: Int, name: String, minRate: Int = 0, maxRate: Int = 1)

object Rates {

  val map = Map(1 -> "selected", 0 -> "unrated", -1 -> "rejected")

  val pairs: Seq[(Option[Int], String)] = Seq(Some(1) -> "selected", Some(0) -> "unrated", Some(-1) -> "rejected")

}

object Round extends SkinnyCRUDMapper[Round] {

  implicit def session: DBSession = autoSession

  override val tableName = "rounds"

  val c = Round.syntax("c")

  val s = SelectionJdbc.syntax("s")

  override lazy val defaultAlias = createAlias("r")

  lazy val r = defaultAlias

  val binaryRound = new Rates(1, "+/-", -1, 1)

  val rateRounds = (3 to 20).map(i => new Rates(i, s"1-$i rating", 1, i))

  val rates = Seq(binaryRound) ++ rateRounds

  val ratesById = rates.groupBy(_.id).mapValues(_.head)

  override def extract(rs: WrappedResultSet, c: ResultName[Round]): Round = new Round(
    id = Some(rs.long(c.id)),
    name = Option(rs.string(c.name)),
    number = rs.int(c.number),
    distribution = rs.int(c.distribution),
    contestId = rs.long(c.contestId),
    rates = Round.ratesById(rs.int(c.rates)),
    limitMin = rs.intOpt(c.limitMin),
    limitMax = rs.intOpt(c.limitMax),
    recommended = rs.intOpt(c.recommended),
    createdAt = rs.timestamp(c.createdAt).toZonedDateTime,
    deletedAt = rs.timestampOpt(c.deletedAt).map(_.toZonedDateTime),
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
    hasCriteria = rs.booleanOpt(c.hasCriteria).getOrElse(false),
    halfStar = rs.booleanOpt(c.halfStar),
    monuments = rs.stringOpt(c.monuments),
    topImages = rs.intOpt(c.topImages),
    specialNomination = rs.stringOpt(c.specialNomination)
  ).withFixedCategories

  def create(round: Round): Round = {
    val id = withSQL {
      insert.into(Round).namedValues(
        column.number -> round.number,
        column.name -> round.name,
        column.contestId -> round.contestId,
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
        column.minImageSize -> round.minImageSize,
        column.halfStar -> round.halfStar,
        column.monuments -> round.monuments,
        column.topImages -> round.topImages,
        column.specialNomination -> round.specialNomination
      )
    }.updateAndReturnGeneratedKey().apply()

    round.copy(id = Some(id))
  }

  def updateRound(id: Long, round: Round) =
    updateById(id)
      .withAttributes(
        'name -> round.name,
        'active -> round.active
      )

  def activeRounds(contestId: Long): Seq[Round] =
    where('contestId -> contestId, 'active -> true)
      .orderBy(r.id).apply()

  def current(user: User): Seq[Round] = {
    user.currentContest.map { contestId =>
      where(sqls
        .eq(r.contestId, contestId).and
        .eq(r.active, true).and
        .exists(
          select.from(SelectionJdbc as s)
            .where
            .eq(s.juryId, user.id.get).and
            .eq(s.roundId, r.id).sql
        ))
        .orderBy(r.id).apply()
    }.getOrElse(Nil)
  }

  def findByContest(contest: Long): Seq[Round] =
    where('contestId -> contest)
      .orderBy(r.id).apply()

  def setActive(id: Long, active: Boolean): Unit =
    updateById(id)
      .withAttributes('active -> active)

  def setInactiveAllInContest(contestId: Long): Unit = withSQL {
    update(Round).set(column.active -> false)
      .where.eq(column.contestId, contestId)
  }.update().apply()

  def countByContest(contestId: Long): Long =
    countBy(sqls.eq(r.contestId, contestId))

  case class RoundStatRow(juror: Long, rate: Int, count: Int)

  def roundUserStat(roundId: Long): Seq[RoundStatRow] =
    sql"""SELECT u.id, s.rate, count(1) FROM users u
      JOIN selection s ON s.jury_id = u.id
    WHERE s.round_id = $roundId
    GROUP BY u.id, s.rate""".map(rs =>
      RoundStatRow(rs.int(1), rs.int(2), rs.int(3))
    ).list().apply()

  def roundRateStat(roundId: Long): Seq[(Int, Int)] =
    sql"""SELECT rate, count(1) FROM
(SELECT DISTINCT s.page_id, s.rate FROM users u
  JOIN selection s ON s.jury_id = u.id
  WHERE s.round_id = $roundId) t
  GROUP BY rate""".map(rs => (rs.int(1), rs.int(2))).list().apply()

}