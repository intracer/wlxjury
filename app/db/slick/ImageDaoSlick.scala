package db.slick

import db.ImageDao
import org.intracer.wmua.{Image, ImageWithRating, User}
import play.api.Play
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfig}
import slick.driver.JdbcProfile
import spray.util.pimpFuture

import scala.concurrent.ExecutionContext.Implicits.global

class ImageDaoSlick extends HasDatabaseConfig[JdbcProfile] with ImageDao {

  protected val dbConfig = DatabaseConfigProvider.get[JdbcProfile](Play.current)

  import driver.api._

  private val Images = TableQuery[ImagesTable]

  class ImagesTable(tag: Tag) extends Table[Image](tag, "images") {

    def pageId = column[Long]("pageId", O.PrimaryKey)

    def contest = column[Long]("contest")

    def title = column[String]("title")

    def url = column[String]("url")

    def pageUrl = column[String]("page_url")

    def width = column[Int]("width")

    def height = column[Int]("height")

    def monumentId = column[Option[String]]("monument_id")

    def * = (pageId, contest, title, url, pageUrl, width, height, monumentId) <>(Image.tupled, Image.unapply)

  }

  override def batchInsert(images: Seq[Image]): Unit =
    db.run(Images ++= images).map(_.get).await

  override def findByMonumentId(monumentId: String): Seq[Image] =
    db.run(Images.filter(_.monumentId === monumentId).result).await

  override def byRating(roundId: Long, rate: Int): Seq[ImageWithRating] =
    ???

  override def byUserSelected(user: User, roundId: Long): Seq[Image] = ???

  override def byRoundSummed(roundId: Long): Seq[ImageWithRating] = ???

  override def bySelectionNotSelected(round: Long): List[Image] = ???

  override def byRoundMerged(round: Long): Seq[ImageWithRating] = ???

  override def findByContest(contest: Long): List[Image] = ???

  override def findAll(): List[Image] = ???

  override def updateResolution(pageId: Long, width: Int, height: Int): Unit = ???

  override def bySelection(round: Long): List[Image] = ???

  override def byUser(user: User, roundId: Long): Seq[Image] = ???

  override def byUserImageWithRating(user: User, roundId: Long): Seq[ImageWithRating] = ???

  override def find(id: Long): Option[Image] = ???

  override def byRatingMerged(rate: Int, round: Long): Seq[ImageWithRating] = ???

  override def byRatingGE(roundId: Long, rate: Int): Seq[ImageWithRating] = ???

  override def bySelectionSelected(round: Long): List[Image] = ???

  override def findWithSelection(id: Long, roundId: Long): Seq[ImageWithRating]= ???

  override def byRound(roundId: Long): Seq[ImageWithRating] = ???

  override def byRatingGEMerged(rate: Int, round: Long): Seq[ImageWithRating] = ???
}
