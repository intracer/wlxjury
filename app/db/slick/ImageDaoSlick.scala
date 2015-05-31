package db.slick

import db.ImageDao
import org.intracer.wmua.{Image, ImageWithRating, User}
import play.api.Play
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfig}
import slick.driver.JdbcProfile

import scala.concurrent.Future

class ImageDaoSlick extends HasDatabaseConfig[JdbcProfile] with ImageDao {

  protected val dbConfig = DatabaseConfigProvider.get[JdbcProfile](Play.current)

  import driver.api._

  private val Images = TableQuery[ImagesTable]

  class ImagesTable(tag: Tag) extends Table[Image](tag, "images") {

    def pageId = column[Long]("pageId", O.PrimaryKey)

    def contest = column[Long]("contest")

    def title = column[String]("title")

    def url = column[Int]("url")

    def pageUrl = column[Int]("height")

    def width = column[Int]("width")

    def height = column[Int]("height")

    def monumentId = column[Option[String]]("monument_id")

    def * = (pageId, contest, title, url, pageUrl, width, height, monumentId) <>(Image.tupled, Image.unapply)

  }

  override def batchInsert(images: Seq[Image]): Future[Int] =
    db.run(Images ++= images).map(_.get)

  override def findByMonumentId(monumentId: String): Future[Seq[Image]] =
    db.run(Images.filter(_.monumentId === monumentId).result)

  override def byRating(roundId: Long, rate: Int): Future[Seq[ImageWithRating]] =
    ???

  override def byUserSelected(user: User, roundId: Long): Future[Seq[Image]] = ???

  override def byRoundSummed(roundId: Long): Future[Seq[ImageWithRating]] = ???

  override def bySelectionNotSelected(round: Long): Future[List[Image]] = ???

  override def byRoundMerged(round: Int): Future[Seq[ImageWithRating]] = ???

  override def findByContest(contest: Long): Future[List[Image]] = ???

  override def findAll(): Future[List[Image]] = ???

  override def updateResolution(pageId: Long, width: Int, height: Int): Future[Unit] = ???

  override def bySelection(round: Long): Future[List[Image]] = ???

  override def byUser(user: User, roundId: Long): Future[Seq[Image]] = ???

  override def byUserImageWithRating(user: User, roundId: Long): Future[Seq[ImageWithRating]] = ???

  override def find(id: Long): Future[Image] = ???

  override def byRatingMerged(rate: Int, round: Int): Future[Seq[ImageWithRating]] = ???

  override def byRatingGE(roundId: Long, rate: Int): Future[Seq[ImageWithRating]] = ???

  override def bySelectionSelected(round: Long): Future[List[Image]] = ???

  override def findWithSelection(id: Long, roundId: Long): Future[Seq[ImageWithRating]] = ???

  override def byRound(roundId: Long): Future[Seq[ImageWithRating]] = ???

  override def byRatingGEMerged(rate: Int, round: Int): Future[Seq[ImageWithRating]] = ???
}
