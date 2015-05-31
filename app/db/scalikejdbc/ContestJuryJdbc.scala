package db.scalikejdbc

import _root_.play.api.i18n.Messages
import db.ContestJuryDao
import org.intracer.wmua.ContestJury
import scalikejdbc._

import scala.concurrent.Future

object ContestJuryJdbc extends SQLSyntaxSupport[ContestJury] with ContestJuryDao {

  var messages: Messages = _

  implicit def session: DBSession = autoSession

  def currentRound(id: Int) =
    find(id).flatMap(cj => Future.successful(cj.currentRound))

  override def byCountry() = findAll().map(_.groupBy(_.country))

  override def byId(id: Int) = find(id)

  def apply(c: SyntaxProvider[ContestJury])(rs: WrappedResultSet): ContestJury = apply(c.resultName)(rs)

  def apply(c: ResultName[ContestJury])(rs: WrappedResultSet): ContestJury = new ContestJury(
    id = rs.longOpt(c.id),
    year = rs.int(c.year),
    country = rs.string(c.country),
    images = rs.stringOpt(c.images),
    currentRound = rs.int(c.currentRound),
    monumentIdTemplate = rs.stringOpt(c.monumentIdTemplate),
    messages
  )

  val c = ContestJuryJdbc.syntax("c")

  override def find(id: Long): Future[ContestJury] = Future {
    withSQL {
      select.from(ContestJuryJdbc as c).where.eq(c.id, id)
    }.map(ContestJuryJdbc(c)).single().apply().get
  }

  override def findAll(): Future[List[ContestJury]] = Future {
    withSQL {
      select.from(ContestJuryJdbc as c)
        .orderBy(c.country)
    }.map(ContestJuryJdbc(c)).list().apply()
  }


  override def countAll(): Future[Long] = Future {
    withSQL {
      select(sqls.count).from(ContestJuryJdbc as c)
    }.map(rs => rs.long(1)).single().apply().get
  }

  override def updateImages(id: Long, images: Option[String]): Unit = withSQL {
    update(ContestJuryJdbc).set(
      column.images -> images
    ).where.eq(column.id, id)
  }.update().apply()

  override def setCurrentRound(id: Int, round: Int): Unit = withSQL {
    update(ContestJuryJdbc).set(
      column.currentRound -> round
    ).where.eq(column.id, id)
  }.update().apply()
}
