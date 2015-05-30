package org.intracer.wmua

import _root_.play.api.i18n.Messages
import scalikejdbc._
import scalikejdbc.WrappedResultSet

case class ContestJury(
                        id: Long,
                        year: Int,
                        country: String,
                        images: Option[String],
                        currentRound: Int,
                        monumentIdTemplate:Option[String],
                        messages: Messages) {
  def name = Messages("wiki.loves.earth." + country, year)(messages)

  def getImages = images.getOrElse("Category:Images from " + name)
}

object ContestJury extends SQLSyntaxSupport[ContestJury] {

  var messages: Messages = _

  def currentRound(id: Int) = find(id).get.currentRound


  def byCountry = findAll().groupBy(_.country)

  def byId(id: Int) = find(id)

  def apply(c: SyntaxProvider[ContestJury])(rs: WrappedResultSet): ContestJury = apply(c.resultName)(rs)

  def apply(c: ResultName[ContestJury])(rs: WrappedResultSet): ContestJury = new ContestJury(
    id = rs.long(c.id),
    year = rs.int(c.year),
    country = rs.string(c.country),
    images = rs.stringOpt(c.images),
    currentRound = rs.int(c.currentRound),
    monumentIdTemplate = rs.stringOpt(c.monumentIdTemplate),
    messages
  )

  val c = ContestJury.syntax("c")

  def find(id: Long)(implicit session: DBSession = autoSession): Option[ContestJury] = withSQL {
    select.from(ContestJury as c).where.eq(c.id, id)
  }.map(ContestJury(c)).single().apply()

  def findAll()(implicit session: DBSession = autoSession): List[ContestJury] = withSQL {
    select.from(ContestJury as c)
      .orderBy(c.country)
  }.map(ContestJury(c)).list().apply()

  def countAll()(implicit session: DBSession = autoSession): Long = withSQL {
    select(sqls.count).from(ContestJury as c)
  }.map(rs => rs.long(1)).single().apply().get

  def updateImages(id: Long, images: Option[String])(implicit session: DBSession = autoSession): Unit = withSQL {
    update(ContestJury).set(
      column.images -> images
    ).where.eq(column.id, id)
  }.update().apply()

  def setCurrentRound(id: Int, round: Int)(implicit session: DBSession = autoSession): Unit = withSQL {
    update(ContestJury).set(
      column.currentRound -> round
    ).where.eq(column.id, id)
  }.update().apply()
}
