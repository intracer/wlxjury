package db.slick

import db.ContestJuryDao
import org.intracer.wmua.ContestJury
import play.api.Play
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfig}

import slick.driver.JdbcProfile

import scala.concurrent.Future

class ContestsDaoSlick extends HasDatabaseConfig[JdbcProfile] with ContestJuryDao {
  protected val dbConfig = DatabaseConfigProvider.get[JdbcProfile](Play.current)

  import driver.api._

  private val Contests = TableQuery[ContestsTable]

  class ContestsTable(tag: Tag) extends Table[ContestJury](tag, "contest_jury") {

    def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)

    def name = column[String]("name")

    def country = column[String]("country")

    def year = column[Int]("year")

    def images = column[String]("images") // commonscat realy

    def currentRound = column[Int]("current_round")

    def monumentIdTemplate = column[String]("monument_id_template")

    def * = (id, name, country, year, images, currentRound, monumentIdTemplate) <>(ContestJury.tupled, ContestJury.unapply)

  }

  override def findAll(): Future[Seq[ContestJury]] = db.run(Contests.result).map(_.toList)

  def insert(cat: ContestJury): Future[Int] = db.run(Contests += cat)

  override def currentRound(id: Int): Future[Int] = byId(id).map(_.currentRound)

  override def byId(id: Int): Future[ContestJury] =
    db.run(Contests.filter(_.id === id).result.head)

  override def countAll(): Future[Int] =
    db.run(Contests.length.result)

  override def byCountry: Future[Map[String, Seq[ContestJury]]] =
    findAll().map(_.groupBy(_.country))

  override def find(id: Long): Future[ContestJury] =
    byId(id.toInt)

  override def setCurrentRound(id: Int, round: Int): Future[Int] =
    db.run(Contests.filter(_.id === id).map(_.currentRound).update(round))

  override def updateImages(id: Long, images: Option[String]): Future[Int] =
    db.run(Contests.filter(_.id === id).map(_.images).update(images.orNull))

}
