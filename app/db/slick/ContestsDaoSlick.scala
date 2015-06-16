package db.slick

import db.ContestJuryDao
import org.intracer.wmua.ContestJury
import play.api.Play
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfig}
import slick.driver.JdbcProfile
import spray.util.pimpFuture

import scala.concurrent.ExecutionContext.Implicits.global

class ContestsDaoSlick extends HasDatabaseConfig[JdbcProfile] with ContestJuryDao {
  protected val dbConfig = DatabaseConfigProvider.get[JdbcProfile](Play.current)

  import driver.api._

  private val Contests = TableQuery[ContestsTable]

  class ContestsTable(tag: Tag) extends Table[ContestJury](tag, "contest_jury") {

    def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)

    def name = column[String]("name")

    def year = column[Int]("year")

    def country = column[String]("country")

    def images = column[Option[String]]("images") // commonscat realy

    def currentRound = column[Long]("current_round")

    def monumentIdTemplate = column[Option[String]]("monument_id_template")

    def * = (id, name, year, country, images, currentRound, monumentIdTemplate) <> (ContestJury.tupled, ContestJury.unapply)

  }

  override def findAll(): Seq[ContestJury] = db.run(Contests.result).map(_.toList).await

  def insert(cat: ContestJury): Int = db.run(Contests += cat).await

  override def currentRound(id: Long): Option[Long] = find(id).map(_.currentRound)

  override def byId(id: Long): ContestJury = find(id).get

  override def countAll(): Long =
    db.run(Contests.length.result).await

  override def byCountry: Map[String, Seq[ContestJury]] =
    findAll().groupBy(_.country)

  override def find(id: Long): Option[ContestJury] =
    db.run(Contests.filter(_.id === id).result.headOption).await

  override def setCurrentRound(id: Long, round: Long): Int =
    db.run(Contests.filter(_.id === id).map(_.currentRound).update(round)).await

  override def updateImages(id: Long, images: Option[String]): Int =
    db.run(Contests.filter(_.id === id).map(_.images).update(images)).await

}
