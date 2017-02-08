package db.scalikejdbc

import _root_.play.api.i18n.Messages
import controllers.Greeting
import org.intracer.wmua.ContestJury
import scalikejdbc._
import skinny.orm.SkinnyCRUDMapper

object ContestJuryJdbc extends SkinnyCRUDMapper[ContestJury] {

  var messages: Messages = _

  override val tableName = "contest_jury"

  implicit def session: DBSession = autoSession

  override lazy val defaultAlias = createAlias("m")

  override def extract(rs: WrappedResultSet, c: ResultName[ContestJury]): ContestJury = ContestJury(
    id = rs.longOpt(c.id),
    name = rs.string(c.name),
    year = rs.int(c.year),
    country = rs.string(c.country),
    images = rs.stringOpt(c.images),
    currentRound = rs.longOpt(c.currentRound),
    monumentIdTemplate = rs.stringOpt(c.monumentIdTemplate),
    greeting = Greeting(
      rs.stringOpt(c.column("greeting")),
      rs.booleanOpt(c.column("use_greeting")).getOrElse(true)
    )
  )

  def updateImages(id: Long, images: Option[String]): Int =
    updateById(id)
      .withAttributes('images -> images)

  def updateGreeting(id: Long, greeting: Greeting): Int =
    updateById(id)
      .withAttributes(
        'greeting -> greeting.text,
        'use_greeting -> greeting.use
      )

  def setCurrentRound(id: Long, round: Option[Long]): Int =
    updateById(id)
      .withAttributes('currentRound -> round)

  def create(id: Option[Long],
                      name: String,
                      year: Int,
                      country: String,
                      images: Option[String],
                      currentRound: Option[Long],
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
      monumentIdTemplate = monumentIdTemplate,
      greeting = Greeting(None, true))
  }

  def batchInsert(contests: Seq[ContestJury]): Seq[Int] = {
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
          column.name -> sqls.?,
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
