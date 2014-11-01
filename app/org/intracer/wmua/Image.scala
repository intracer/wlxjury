package org.intracer.wmua

import client.dto.Page
import scalikejdbc._

case class Image(pageId: Long, contest: Long, title: String,
                 url: String, pageUrl: String,
                 width: Int,
                 height: Int,
                 monumentId: Option[String]) extends Ordered[Image]{

  def compare(that: Image) =  (this.pageId - that.pageId).signum

  def region: Option[String] = monumentId.map(_.split("-")(0))

}

object ImageJdbc extends SQLSyntaxSupport[Image] {

  def fromPage(page: Page, contest: ContestJury):Option[Image] = {
    try {
      for (imageInfo <- page.imageInfo.headOption)
      yield new Image(page.pageid, contest.id, page.title, imageInfo.url, imageInfo.descriptionUrl, imageInfo.width, imageInfo.height, None)
    } catch  {
      case e: Throwable =>
        println(e)
        throw e
    }
  }

  override val tableName = "images"

  val c = ImageJdbc.syntax("c")

  def apply(c: SyntaxProvider[Image])(rs: WrappedResultSet): Image = apply(c.resultName)(rs)

  def apply(c: ResultName[Image])(rs: WrappedResultSet): Image = new Image(
    pageId = rs.long(c.pageId),
    contest = rs.long(c.contest),
    title = rs.string(c.title),
    url = rs.string(c.url),
    pageUrl = rs.string(c.pageUrl),
    width = rs.int(c.width),
    height = rs.int(c.height),
    monumentId = rs.stringOpt(c.monumentId)
  )

  def batchInsert(images: Seq[Image]) {
    val column = ImageJdbc.column
    DB localTx { implicit session =>
      val batchParams: Seq[Seq[Any]] = images.map(i => Seq(
        i.pageId,
        i.contest,
        i.title,
        i.url,
        i.pageUrl,
        i.width,
        i.height,
        i.monumentId
      ))
      withSQL {
        insert.into(ImageJdbc).namedValues(
          column.pageId -> sqls.?,
          column.contest -> sqls.?,
          column.title -> sqls.?,
          column.url -> sqls.?,
          column.pageUrl -> sqls.?,
          column.width -> sqls.?,
          column.height -> sqls.?,
          column.monumentId -> sqls.?
        )
      }.batch(batchParams: _*).apply()
    }
  }

  def updateResolution(pageId: Long, width: Int, height: Int)(implicit session: DBSession = autoSession): Unit = withSQL {
    update(ImageJdbc).set(
      column.width -> width,
      column.height -> height
    ).where.eq(column.pageId, pageId)
  }.update().apply()

  def findAll()(implicit session: DBSession = autoSession): List[Image] = withSQL {
    select.from(ImageJdbc as c)
      //      .where.append(isNotDeleted)
      .orderBy(c.pageId)
  }.map(ImageJdbc(c)).list().apply()

  def findByContest(contest: Long)(implicit session: DBSession = autoSession): List[Image] = withSQL {
    select.from(ImageJdbc as c)
      .where //.append(isNotDeleted)
      // .and
      .eq(c.contest, contest)
      .orderBy(c.pageId)
  }.map(ImageJdbc(c)).list().apply()

  def find(id: Long)(implicit session: DBSession = autoSession): Option[Image] = withSQL {
    select.from(ImageJdbc as c).where.eq(c.pageId, id) //.and.append(isNotDeleted)
  }.map(ImageJdbc(c)).single().apply()

  def bySelection(round: Long)(implicit session: DBSession = autoSession): List[Image] = withSQL {
    select.from(ImageJdbc as c)
      .innerJoin(Selection as Selection.s).on(c.pageId, Selection.s.pageId)
      .where.eq(Selection.s.round, round)
  }.map(ImageJdbc(c)).list().apply()

  def bySelectionNotSelected(round: Long)(implicit session: DBSession = autoSession): List[Image] = withSQL {
    select.from(ImageJdbc as c)
      .innerJoin(Selection as Selection.s).on(c.pageId, Selection.s.pageId)
      .where.eq(Selection.s.round, round)
      .and
      .eq(Selection.s.rate, 0)
  }.map(ImageJdbc(c)).list().apply()

  def bySelectionSelected(round: Long)(implicit session: DBSession = autoSession): List[Image] = withSQL {
    select.from(ImageJdbc as c)
      .innerJoin(Selection as Selection.s).on(c.pageId, Selection.s.pageId)
      .where.eq(Selection.s.round, round).and
      .ne(Selection.s.rate, 0)
  }.map(ImageJdbc(c)).list().apply()

  def byUser(user: User, roundId: Long)(implicit session: DBSession = autoSession): Seq[Image] = withSQL {
    select.from(ImageJdbc as c)
      .innerJoin(Selection as Selection.s).on(c.pageId, Selection.s.pageId)
      .where.eq(Selection.s.juryId, user.id).and
      .eq(Selection.s.round, roundId)
    //      .append(isNotDeleted)
  }.map(ImageJdbc(c)).list().apply()

  def byUserSelected(user: User, roundId: Long)(implicit session: DBSession = autoSession): Seq[Image] = withSQL {
    select.from(ImageJdbc as c)
      .innerJoin(Selection as Selection.s).on(c.pageId, Selection.s.pageId)
      .where.eq(Selection.s.juryId, user.id).and
      .eq(Selection.s.round, roundId).and
      .ne(Selection.s.rate, 0)
    //      .append(isNotDeleted)
  }.map(ImageJdbc(c)).list().apply()

  def byUserImageWithRating(user: User, roundId: Long)(implicit session: DBSession = autoSession): Seq[ImageWithRating] = withSQL {
    select.from(ImageJdbc as c)
      .innerJoin(Selection as Selection.s).on(c.pageId, Selection.s.pageId)
      .where.eq(Selection.s.juryId, user.id).and
      .eq(Selection.s.round, roundId)
    //      .append(isNotDeleted)
  }.map(rs => (ImageJdbc(c)(rs), Selection(Selection.s)(rs))).list().apply().map{case (i,s ) => ImageWithRating(i,Seq(s))}

  def byRating(roundId: Long, rate: Int)(implicit session: DBSession = autoSession): Seq[ImageWithRating] = withSQL {
    select.from(ImageJdbc as c)
      .innerJoin(Selection as Selection.s).on(c.pageId, Selection.s.pageId)
      .where.eq(Selection.s.rate, rate).and
      .eq(Selection.s.round, roundId)
    //      .append(isNotDeleted)
  }.map(rs => (ImageJdbc(c)(rs), Selection(Selection.s)(rs))).list().apply().map{case (i,s ) => ImageWithRating(i,Seq(s))}

  def byRatingGE(roundId: Long, rate: Int)(implicit session: DBSession = autoSession): Seq[ImageWithRating] = withSQL {
    select.from(ImageJdbc as c)
      .innerJoin(Selection as Selection.s).on(c.pageId, Selection.s.pageId)
      .where.ge(Selection.s.rate, rate).and
      .eq(Selection.s.round, roundId)
    //      .append(isNotDeleted)
  }.map(rs => (ImageJdbc(c)(rs), Selection(Selection.s)(rs))).list().apply().map{case (i,s ) => ImageWithRating(i,Seq(s))}


  def byRound(roundId: Long)(implicit session: DBSession = autoSession): Seq[ImageWithRating] = withSQL {
    select.from(ImageJdbc as c)
      .innerJoin(Selection as Selection.s).on(c.pageId, Selection.s.pageId)
      .where.eq(Selection.s.round, roundId)
    //      .append(isNotDeleted)
  }.map(rs => (ImageJdbc(c)(rs), Selection(Selection.s)(rs))).list().apply().map{case (i,s ) => ImageWithRating(i,Seq(s))}

  def byRatingMerged(rate: Int, round: Int): Seq[ImageWithRating] = {
    val raw = ImageJdbc.byRating(round, rate)
    val merged = raw.groupBy(_.pageId).mapValues(iws => new ImageWithRating(iws.head.image, iws.map(_.selection.head)))
    merged.values.toSeq
  }

  def byRatingGEMerged(rate: Int, round: Int): Seq[ImageWithRating] = {
    val raw = ImageJdbc.byRatingGE(round, rate)
    val merged = raw.groupBy(_.pageId).mapValues(iws => new ImageWithRating(iws.head.image, iws.map(_.selection.head)))
    merged.values.toSeq
  }

  def byRoundMerged(round: Int): Seq[ImageWithRating] = {
    val raw = ImageJdbc.byRound(round)
    val merged = raw.groupBy(_.pageId).mapValues(iws => new ImageWithRating(iws.head.image, iws.map(_.selection.head)))
    merged.values.toSeq
  }


}
