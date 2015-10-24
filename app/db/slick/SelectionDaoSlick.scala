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

    def juryId = column[Long]("jury_id")

    def round = column[Long]("round")

    //    def createdAt = column[DateTime]("created_at")
    //
    //    def deletedAt = column[Option[DateTime]]("deleted_at")

    def * = (id, pageId, rate, juryId, round /*createdAt, deletedAt*/ ) <>(SelectionTable.fromDb, SelectionTable.toDb)

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

  override def create(pageId: Long, rate: Int, juryId: Long, roundId: Long, createdAt: DateTime): Selection = ???

  override def allJurors(roundId: Long): Int = ???

  override def byRound(roundId: Long): Seq[Selection] = ???
}

object SelectionTable {

  def fromDb(t: (
    Long,
      Long,
      Int,
      Long,
      Long
    ))
  =
    new Selection(
      id = t._1,
      pageId = t._2,
      rate = t._3,
      juryId = t._4,
      round = t._5
    )

  def toDb(s: Selection) =
    Some(
      s.id,
      s.pageId,
      s.rate,
      s.juryId,
      s.round
    )
}
