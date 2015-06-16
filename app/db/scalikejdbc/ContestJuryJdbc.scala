package db.scalikejdbc

import _root_.play.api.i18n.Messages
import db.ContestJuryDao
import org.intracer.wmua.ContestJury
import scalikejdbc._

object ContestJuryJdbc extends SQLSyntaxSupport[ContestJury] with ContestJuryDao {

  var messages: Messages = _

  implicit def session: DBSession = autoSession

  override def currentRound(id: Long): Option[Long] =
    find(id).map(_.currentRound)

  // find(id).flatMap(cj => Future.successful(cj.currentRound))

  override def byCountry = findAll().groupBy(_.country)

  //findAll().map(_.groupBy(_.country))

  override def byId(id: Long) = find(id).get

  def apply(c: SyntaxProvider[ContestJury])(rs: WrappedResultSet): ContestJury = apply(c.resultName)(rs)

  def apply(c: ResultName[ContestJury])(rs: WrappedResultSet): ContestJury = new ContestJury(
    id = rs.longOpt(c.id),
    name = rs.string(c.name),
    year = rs.int(c.year),
    country = rs.string(c.country),
    images = rs.stringOpt(c.images),
    currentRound = rs.int(c.currentRound),
    monumentIdTemplate = rs.stringOpt(c.monumentIdTemplate)
    //, messages
  )

  val c = ContestJuryJdbc.syntax("c")

  override def find(id: Long): Option[ContestJury] = //Future {
    withSQL {
      select.from(ContestJuryJdbc as c).where.eq(c.id, id)
    }.map(ContestJuryJdbc(c)).single().apply()

  // }

  override def findAll(): List[ContestJury] = //Future {
    withSQL {
      select.from(ContestJuryJdbc as c)
        .orderBy(c.country)
    }.map(ContestJuryJdbc(c)).list().apply()

  // }


  override def countAll(): Long = //Future {
    withSQL {
      select(sqls.count).from(ContestJuryJdbc as c)
    }.map(rs => rs.long(1)).single().apply().get

  // }

  override def updateImages(id: Long, images: Option[String]): Int = withSQL {
    update(ContestJuryJdbc).set(
      column.images -> images
    ).where.eq(column.id, id)
  }.update().apply()

  override def setCurrentRound(id: Long, round: Long): Int = withSQL {
    update(ContestJuryJdbc).set(
      column.currentRound -> round
    ).where.eq(column.id, id)
  }.update().apply()
}
