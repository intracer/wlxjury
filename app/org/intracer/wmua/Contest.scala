package org.intracer.wmua

import scalikejdbc._
import scalikejdbc.WrappedResultSet

case class Contest(id: Long, year: Int, country: String, images: Option[String], currentRound: Int) {
  def name = s"Wiki Loves Earth $year in $country"

  def getImages = images.getOrElse("Category:Images from " + name)
}

object Contest extends SQLSyntaxSupport[Contest] {

  def currentRound(id: Int) = find(id).get.currentRound


  def byCountry = findAll().groupBy(_.country)

  def byId(id: Int) = find(id)

  def apply(c: SyntaxProvider[Contest])(rs: WrappedResultSet): Contest = apply(c.resultName)(rs)

  def apply(c: ResultName[Contest])(rs: WrappedResultSet): Contest = new Contest(
    id = rs.long(c.id),
    year = rs.int(c.year),
    country = rs.string(c.country),
    images = rs.stringOpt(c.images),
    currentRound = rs.int(c.currentRound)
  )

  val c = Contest.syntax("c")

  def find(id: Long)(implicit session: DBSession = autoSession): Option[Contest] = withSQL {
    select.from(Contest as c).where.eq(c.id, id)
  }.map(Contest(c)).single.apply()

  def findAll()(implicit session: DBSession = autoSession): List[Contest] = withSQL {
    select.from(Contest as c)
      .orderBy(c.country)
  }.map(Contest(c)).list.apply()

  def countAll()(implicit session: DBSession = autoSession): Long = withSQL {
    select(sqls.count).from(Contest as c)
  }.map(rs => rs.long(1)).single.apply().get

  def updateImages(id: Long, images: Option[String])(implicit session: DBSession = autoSession): Unit = withSQL {
    update(Contest).set(
      column.images -> images
    ).where.eq(column.id, id)
  }.update.apply()

  def setCurrentRound(id: Int, round: Int)(implicit session: DBSession = autoSession): Unit = withSQL {
    update(Contest).set(
      column.currentRound -> round
    ).where.eq(column.id, id)
  }.update.apply()


}
