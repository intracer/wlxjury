package db.slick

import db.SelectionDao
import org.intracer.wmua.{Selection, User}
import org.joda.time.DateTime
import play.api.Play
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfig}
import slick.driver.JdbcProfile

class SelectionDaoSlick extends HasDatabaseConfig[JdbcProfile] with SelectionDao {

  protected val dbConfig = DatabaseConfigProvider.get[JdbcProfile](Play.current)

  import driver.api._

  private val Selections = TableQuery[SelectionTable]

  class SelectionTable(tag: Tag) extends Table[Selection](tag, "selection") {

    def id = column[Long]("id", O.PrimaryKey)

    def pageId = column[Long]("page_id")

    def rate = column[Int]("rate")

    def juryId = column[Int]("jury_id")

    def round = column[Int]("round")

    def createdAt = column[DateTime]("created_at")

    def deletedAt = column[Option[DateTime]]("deleted_at")


    def * = (id, pageId, rate, round, createdAt, deletedAt) <>(Selection.tupled, Selection.unapply)

  }

  override def byUser(user: User, roundId: Long): Seq[Selection] = ???

  override def byUserSelected(user: User, roundId: Long): Seq[Selection] = ???

  override def byUserNotSelected(user: User, roundId: Long): Seq[Selection] = ???

  override def findAll(): List[Selection] = ???

  override def countAll(): Long = ???

  override def destroy(pageId: Long, juryId: Long, round: Long): Unit = ???

  override def byRoundAndImageWithJury(roundId: Long, imageId: Long): Seq[(Selection, User)] = ???

  override def batchInsert(selections: Seq[Selection]): Unit = ???

  override def activeJurors(roundId: Long): Int = ???

  override def byRoundSelected(roundId: Long): Seq[Selection] = ???

  override def find(id: Long): Option[Selection] = ???

  override def rate(pageId: Long, juryId: Long, round: Long, rate: Int): Unit = ???

  override def create(pageId: Long, rate: Int, fileid: String, juryId: Int, round: Int, createdAt: DateTime): Selection = ???

  override def allJurors(roundId: Long): Int = ???

  override def byRound(roundId: Long): Seq[Selection] = ???
}
