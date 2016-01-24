package db.scalikejdbc

import _root_.play.api.i18n.Messages
import db.ContestJuryDao
import org.intracer.wmua.ContestJury
import scalikejdbc._

object ContestJuryJdbc extends SQLSyntaxSupport[ContestJury] with ContestJuryDao {

  var messages: Messages = _

  override val tableName = "contest_jury"

  implicit def session: DBSession = autoSession

  val c = ContestJuryJdbc.syntax("c")

  override def currentRound(id: Long): Option[Long] =
    find(id).map(_.currentRound)

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

  override def find(id: Long): Option[ContestJury] =
    withSQL {
      select.from(ContestJuryJdbc as c).where.eq(c.id, id)
    }.map(ContestJuryJdbc(c)).single().apply()

  override def find(name: String, country: String, year: Int): Option[ContestJury]  = {
    withSQL {
      select.from(ContestJuryJdbc as c).where
        .eq(c.name, name).and
        .eq(c.country, country).and
        .eq(c.year, year)
    }.map(ContestJuryJdbc(c)).single().apply()
  }

  override def findAll(): List[ContestJury] =
    withSQL {
      select.from(ContestJuryJdbc as c)
        .orderBy(c.id)
    }.map(ContestJuryJdbc(c)).list().apply()

  override def countAll(): Long =
    withSQL {
      select(sqls.count).from(ContestJuryJdbc as c)
    }.map(rs => rs.long(1)).single().apply().get

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

  override def create(id: Option[Long],
                      name: String,
                      year: Int,
                      country: String,
                      images: Option[String],
                      currentRound: Long,
                      monumentIdTemplate: Option[String]): ContestJury = {
    val dbId = withSQL {
      insert.into(ContestJuryJdbc).namedValues(
        column.id -> id,
        column.name -> name,
        column.year -> year,
        column.country -> country,
        column.images -> images,
        column.currentRound -> currentRound,
        column.monumentIdTemplate -> monumentIdTemplate)
    }.updateAndReturnGeneratedKey().apply()

    ContestJury(id = Some(dbId),
      name = name,
      year = year,
      country = country,
      images = images,
      currentRound = currentRound,
      monumentIdTemplate = monumentIdTemplate)
  }

  override def batchInsert(contests: Seq[ContestJury]): Seq[Int] = {
    val column = ContestJuryJdbc.column
    DB localTx { implicit session =>
      val batchParams: Seq[Seq[Any]] = contests.map(c => Seq(
        c.id,
        c.name,
        c.year,
        c.country,
        c.images,
        c.currentRound,
        c.monumentIdTemplate
      ))
      withSQL {
        insert.into(ContestJuryJdbc).namedValues(
          column.id -> sqls.?,
          column.name-> sqls.?,
          column.year -> sqls.?,
          column.country -> sqls.?,
          column.images -> sqls.?,
          column.currentRound -> sqls.?,
          column.monumentIdTemplate -> sqls.?
        )
      }.batch(batchParams: _*).apply()
    }
  }
}
