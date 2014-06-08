package org.intracer.wmua

import scalikejdbc._
import client.dto.Page

case class Image(pageid: Long, contest: Int, title: String, url: String, pageUrl: String, lastRound: Int) {

}

object Image extends SQLSyntaxSupport[Image] {

  val imagesByContest = collection.mutable.Map[Long, Seq[Page]]()

  override val tableName = "images"

  val c = Image.syntax("c")

  def apply(c: SyntaxProvider[Image])(rs: WrappedResultSet): Image = apply(c.resultName)(rs)

  def apply(c: ResultName[Image])(rs: WrappedResultSet): Image = new Image(
    pageid = rs.long(c.pageid),
    contest = rs.int(c.contest),
    title = rs.string(c.title),
    url = rs.string(c.url),
    pageUrl = rs.string(c.pageUrl),
    lastRound = rs.int(c.lastRound)
  )

  def batchInsert(images: Seq[Image]) {
    val column = Image.column
    DB localTx { implicit session =>
      val batchParams: Seq[Seq[Any]] = images.map(i => Seq(
        i.pageid,
        i.contest,
        i.title,
        i.url,
        i.pageUrl,
        i.lastRound))
      withSQL {
        insert.into(Image).namedValues(
          column.pageid -> sqls.?,
          column.contest -> sqls.?,
          column.title -> sqls.?,
          column.url -> sqls.?,
          column.pageUrl -> sqls.?,
          column.lastRound -> sqls.?
        )
      }.batch(batchParams: _*).apply()
    }
  }

  def findAll()(implicit session: DBSession = autoSession): List[Image] = withSQL {
    select.from(Image as c)
//      .where.append(isNotDeleted)
      .orderBy(c.pageid)
  }.map(Image(c)).list.apply()

  def findByContest(contest: Long)(implicit session: DBSession = autoSession): List[Image] = withSQL {
    select.from(Image as c)
      .where//.append(isNotDeleted)
      // .and
      .eq(c.contest, contest)
      .orderBy(c.pageid)
  }.map(Image(c)).list.apply()

  def find(id: Long)(implicit session: DBSession = autoSession): Option[Image] = withSQL {
    select.from(Image as c).where.eq(c.pageid, id)//.and.append(isNotDeleted)
  }.map(Image(c)).single.apply()

}
