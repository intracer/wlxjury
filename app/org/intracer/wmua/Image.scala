package org.intracer.wmua

import scalikejdbc._
import client.dto.Page

case class Image(pageid: Long, contest: Long, title: String,
                 url: String, pageUrl: String,
                 lastRound: Int,
                 width: Int,
                 height: Int) {

}

object Image extends SQLSyntaxSupport[Image] {


  def fromPage(page: Page, contest: Contest):Option[Image] = {
    for (imageInfo <- page.imageInfo.headOption)
     yield new Image(page.pageid, contest.id, page.title, imageInfo.url, imageInfo.descriptionUrl, 0, imageInfo.width, imageInfo.height)
  }

  val imagesByContest = collection.mutable.Map[Long, Seq[Image]]()

  override val tableName = "images"

  val c = Image.syntax("c")

  def apply(c: SyntaxProvider[Image])(rs: WrappedResultSet): Image = apply(c.resultName)(rs)

  def apply(c: ResultName[Image])(rs: WrappedResultSet): Image = new Image(
    pageid = rs.long(c.pageid),
    contest = rs.long(c.contest),
    title = rs.string(c.title),
    url = rs.string(c.url),
    pageUrl = rs.string(c.pageUrl),
    lastRound = rs.int(c.lastRound),
    width = rs.int(c.width),
    height = rs.int(c.height)
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
        i.lastRound,
        i.width,
        i.height))
      withSQL {
        insert.into(Image).namedValues(
          column.pageid -> sqls.?,
          column.contest -> sqls.?,
          column.title -> sqls.?,
          column.url -> sqls.?,
          column.pageUrl -> sqls.?,
          column.lastRound -> sqls.?,
          column.width -> sqls.?,
          column.height -> sqls.?
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
      .where //.append(isNotDeleted)
      // .and
      .eq(c.contest, contest)
      .orderBy(c.pageid)
  }.map(Image(c)).list.apply()

  def find(id: Long)(implicit session: DBSession = autoSession): Option[Image] = withSQL {
    select.from(Image as c).where.eq(c.pageid, id) //.and.append(isNotDeleted)
  }.map(Image(c)).single.apply()

}
