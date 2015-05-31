package db.slick

import db.RoundDao
import db.scalikejdbc.RoundJdbc
import db.scalikejdbc.RoundJdbc._
import org.intracer.wmua.{Round, Image}
import org.joda.time.DateTime
import _root_.play.api.Play
import _root_.play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfig}
import scalikejdbc.QueryDSLFeature.withSQL
import scalikejdbc._
import slick.driver.JdbcProfile

class RoundDaoSlick extends HasDatabaseConfig[JdbcProfile] with RoundDao {

  protected val dbConfig = DatabaseConfigProvider.get[JdbcProfile](Play.current)

  import driver.api._

  private val Rounds = TableQuery[Round]

  class RoundsTable(tag: Tag) extends Table[Image](tag, "rounds") {

    def id = column[Option[Long]]("id", O.PrimaryKey)

    def number = column[Int]("number")

    def name = column[Option[String]("name")

    def contest = column[Long]("contest")

    def roles = column[String]("roles")

    def distribution = column[Int]("distribution")

    def rates = column[Int]("rates")

    def limitMin = column[Int]("limit_min")

    def limitMax = column[Int]("limit_max")

    def active = column[Boolean]("active")

    def optionalRate = column[Int]("optional_rate")

    def juryOrgView = column[Int]("jury_org_view")

    def * = (id, number, name, contest, roles, distribution, rates, limitMin, limitMax, active, optionalRate, juryOrgView
      ) <> (RoundsTable.toDb, RoundsTable.fromDb)

  }

}

object RoundsTable {
  def toDb(
            Option[Long],  // id
  Int, // number
  Option[String], // name
  Int, // contest
  Set[String], // roles
  Int,
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

  def fromDb
}