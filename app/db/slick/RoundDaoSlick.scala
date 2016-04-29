package db.slick

import _root_.play.api.Play
import _root_.play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfig}
import db.RoundDao
import org.intracer.wmua.{Round, User}
import org.joda.time.DateTime
import slick.driver.JdbcProfile

class RoundDaoSlick extends HasDatabaseConfig[JdbcProfile] with RoundDao {

  protected val dbConfig = DatabaseConfigProvider.get[JdbcProfile](Play.current)

  import driver.api._

  private val Rounds = TableQuery[RoundsTable]

  class RoundsTable(tag: Tag) extends Table[Round](tag, "rounds") {

    def id = column[Option[Long]]("id", O.PrimaryKey)

    def number = column[Int]("number")

    def name = column[Option[String]]("name")

    def contest = column[Long]("contest")

    def roles = column[String]("roles")

    def distribution = column[Int]("distribution")

    def rates = column[Int]("rates")

    def limitMin = column[Option[Int]]("limit_min")

    def limitMax = column[Option[Int]]("limit_max")

    def recommended = column[Option[Int]]("recommended")

    def active = column[Boolean]("active")

    def optionalRate = column[Boolean]("optional_rate")

    def juryOrgView = column[Boolean]("jury_org_view")

    def * = (id, number, name, contest, roles, distribution, rates, limitMin, limitMax, recommended, active, optionalRate, juryOrgView
      ) <> (RoundsTable.fromDb, RoundsTable.toDb)

  }

  override def activeRounds(contestId: Long): Seq[Round] = ???

  override def findByContest(contest: Long): Seq[Round] = ???

  override def findAll(): Seq[Round] = ???

  override def updateRound(id: Long, round: Round): Unit = ???

  override def countByContest(contest: Long): Int = ???

  override def find(id: Long): Option[Round] = ???

  override def current(user: User): Option[Round] = ???

  override def create(number: Int, name: Option[String], contest: Long, roles: String, distribution: Int, rates: Int, limitMin: Option[Int], limitMax: Option[Int], recommended: Option[Int], createdAt: DateTime): Round = ???

  override def create(round: Round): Round = ???

  override def setActive(id: Long, active: Boolean): Unit = ???
}

object RoundsTable {

  def fromDb(t: (
    Option[Long], // id
      Int, // number
      Option[String], // name
      Long, // contest
      String, // roles
      Int, // distribution
      Int, // rates,
      Option[Int], // limitMin
      Option[Int], // limitMax
      Option[Int], // recommended
      Boolean, //  r.active,
      Boolean, //  r.optionalRate,
      Boolean //  r.juryOrgView

    //      DateTime)) //  createdAt:
    ))
  =
    new Round(
      id = t._1,
      number = t._2,
      name = t._3,
      contest = t._4,
      roles = t._5.split(",").toSet,
      distribution = t._6,
      rates = Round.ratesById(t._7),
      limitMin = t._8,
      limitMax = t._9,
      recommended = t._10,
      active = t._11,
      optionalRate = t._12,
      juryOrgView = t._13
      //      createdAt = t._11
    )

  def toDb(r: Round) =
    Some(r.id,
      r.number,
      r.name,
      r.contest,
      r.roles.mkString(","),
      r.distribution,
      r.rates.id,
      r.limitMin,
      r.limitMax,
      r.recommended,
      r.active,
      r.optionalRate,
      r.juryOrgView
      //      r.recommended,
      //      r.createdAt
    )
}