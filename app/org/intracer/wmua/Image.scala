package org.intracer.wmua

import scalikejdbc._
import client.dto.Page
import org.intracer.wmua.Selection

case class Image(pageId: Long, contest: Long, title: String,
                 url: String, pageUrl: String,
                 lastRound: Int,
                 width: Int,
                 height: Int,
                 monumentId: Option[String]) extends Ordered[Image]{

  def compare(that: Image) =  (this.pageId - that.pageId).signum

}

object Image extends SQLSyntaxSupport[Image] {


  def fromPage(page: Page, contest: Contest):Option[Image] = {
    for (imageInfo <- page.imageInfo.headOption)
     yield new Image(page.pageid, contest.id, page.title, imageInfo.url, imageInfo.descriptionUrl, 0, imageInfo.width, imageInfo.height, None)
  }

  private  val imagesByContest = collection.mutable.Map[Long, Seq[Image]]()

  def byContest(id: Long) = imagesByContest.getOrElseUpdate(id, findByContest(id))

  override val tableName = "images"

  val c = Image.syntax("c")

  def apply(c: SyntaxProvider[Image])(rs: WrappedResultSet): Image = apply(c.resultName)(rs)

  def apply(c: ResultName[Image])(rs: WrappedResultSet): Image = new Image(
    pageId = rs.long(c.pageId),
    contest = rs.long(c.contest),
    title = rs.string(c.title),
    url = rs.string(c.url),
    pageUrl = rs.string(c.pageUrl),
    lastRound = rs.int(c.lastRound),
    width = rs.int(c.width),
    height = rs.int(c.height),
    monumentId = rs.stringOpt(c.monumentId)
  )

  def batchInsert(images: Seq[Image]) {
    val column = Image.column
    DB localTx { implicit session =>
      val batchParams: Seq[Seq[Any]] = images.map(i => Seq(
        i.pageId,
        i.contest,
        i.title,
        i.url,
        i.pageUrl,
        i.lastRound,
        i.width,
        i.height,
        i.monumentId
      ))
      withSQL {
        insert.into(Image).namedValues(
          column.pageId -> sqls.?,
          column.contest -> sqls.?,
          column.title -> sqls.?,
          column.url -> sqls.?,
          column.pageUrl -> sqls.?,
          column.lastRound -> sqls.?,
          column.width -> sqls.?,
          column.height -> sqls.?,
          column.monumentId -> sqls.?
        )
      }.batch(batchParams: _*).apply()
    }
  }

  def findAll()(implicit session: DBSession = autoSession): List[Image] = withSQL {
    select.from(Image as c)
      //      .where.append(isNotDeleted)
      .orderBy(c.pageId)
  }.map(Image(c)).list.apply()

  def findByContest(contest: Long)(implicit session: DBSession = autoSession): List[Image] = withSQL {
    select.from(Image as c)
      .where //.append(isNotDeleted)
      // .and
      .eq(c.contest, contest)
      .orderBy(c.pageId)
  }.map(Image(c)).list.apply()

  def find(id: Long)(implicit session: DBSession = autoSession): Option[Image] = withSQL {
    select.from(Image as c).where.eq(c.pageId, id) //.and.append(isNotDeleted)
  }.map(Image(c)).single.apply()

  def bySelection(round: Long)(implicit session: DBSession = autoSession): List[Image] = withSQL {
    select.from(Image as c)
      .innerJoin(Selection as Selection.s).on(c.pageId, Selection.s.pageId)
      .where.eq(Selection.s.round, round)
  }.map(Image(c)).list.apply()

  def bySelectionNotSelected(round: Long)(implicit session: DBSession = autoSession): List[Image] = withSQL {
    select.from(Image as c)
      .innerJoin(Selection as Selection.s).on(c.pageId, Selection.s.pageId)
      .where.eq(Selection.s.round, round)
      .and
      .eq(Selection.s.rate, 0)
  }.map(Image(c)).list.apply()

  def bySelectionSelected(round: Long)(implicit session: DBSession = autoSession): List[Image] = withSQL {
    select.from(Image as c)
      .innerJoin(Selection as Selection.s).on(c.pageId, Selection.s.pageId)
      .where.eq(Selection.s.round, round).and
      .ne(Selection.s.rate, 0)
  }.map(Image(c)).list.apply()


  def byUser(user: User, roundId: Long)(implicit session: DBSession = autoSession): Seq[Image] = withSQL {
    select.from(Image as c)
      .innerJoin(Selection as Selection.s).on(c.pageId, Selection.s.pageId)
      .where.eq(Selection.s.juryid, user.id).and
      .eq(Selection.s.round, roundId)
//      .append(isNotDeleted)
  }.map(Image(c)).list.apply()

  def byUserSelected(user: User, roundId: Long)(implicit session: DBSession = autoSession): Seq[Image] = withSQL {
    select.from(Image as c)
      .innerJoin(Selection as Selection.s).on(c.pageId, Selection.s.pageId)
      .where.eq(Selection.s.juryid, user.id).and
      .eq(Selection.s.round, roundId).and
      .ne(Selection.s.rate, 0)
    //      .append(isNotDeleted)
  }.map(Image(c)).list.apply()

  def byUserImageWithRating(user: User, roundId: Long)(implicit session: DBSession = autoSession): Seq[ImageWithRating] = withSQL {
    select.from(Image as c)
      .innerJoin(Selection as Selection.s).on(c.pageId, Selection.s.pageId)
      .where.eq(Selection.s.juryid, user.id).and
      .eq(Selection.s.round, roundId)
    //      .append(isNotDeleted)
  }.map(rs => (Image(c)(rs), Selection(Selection.s)(rs))).list.apply().map{case (i,s ) => ImageWithRating(i,s)}


}
