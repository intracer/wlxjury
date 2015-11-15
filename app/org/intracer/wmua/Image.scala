package org.intracer.wmua

import java.text.DecimalFormat

import org.scalawiki.dto.Page
import scalikejdbc._
import scalikejdbc.interpolation.SQLSyntax._

case class Image(pageId: Long,
                 contest: Long,
                 title: String,
                 url: String,
                 pageUrl: String,
                 width: Int,
                 height: Int,
                 monumentId: Option[String]) extends Ordered[Image] {

  def compare(that: Image) = (this.pageId - that.pageId).signum

  def region: Option[String] = monumentId.map(_.split("-")(0))

  def resizeTo(resizeToX: Int, resizeToY: Int): Int =
    ImageUtil.resizeTo(width, height, resizeToX, resizeToY)

  def resolutionStr = s"$width x $height"

  def mpxStr = ImageUtil.fmt.format(width * height / 1000000.0)

}

object ImageUtil {

  def fmt = new DecimalFormat("0.0")

  def resizeTo(w: Int, h: Int, resizeToX: Int, resizeToY: Int): Int = {
    val xRatio = w.toDouble / resizeToX
    val yRatio = h.toDouble / resizeToY

    val width = Math.min(resizeToX, w / yRatio)
    width.toInt
  }


}

object ImageJdbc extends SQLSyntaxSupport[Image] {

  private val isNotDeleted = sqls.isNull(Selection.s.deletedAt)

  def fromPage(page: Page, contest: ContestJury): Option[Image] = {
    try {
      for (imageInfo <- page.images.headOption)
        yield new Image(page.id.get, contest.id, page.title, imageInfo.url.get, imageInfo.pageUrl.get, imageInfo.width.get, imageInfo.height.get, None)
    } catch {
      case e: Throwable =>
        println(e)
        throw e
    }
  }

  override val tableName = "images"

  val i = ImageJdbc.syntax("i")

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

  def updateMonumentId(pageId: Long, monumentId: String)(implicit session: DBSession = autoSession): Unit = withSQL {
    update(ImageJdbc).set(
      column.monumentId -> monumentId
    ).where.eq(column.pageId, pageId)
  }.update().apply()


  def deleteImage(pageId: Long)(implicit session: DBSession = autoSession): Unit = withSQL {
    delete.from(ImageJdbc).where.eq(ImageJdbc.column.pageId, pageId)
  }.update().apply()

  def findAll()(implicit session: DBSession = autoSession): List[Image] = withSQL {
    select.from(ImageJdbc as i)
      //      .where.append(isNotDeleted)
      .orderBy(i.pageId)
  }.map(ImageJdbc(i)).list().apply()

  def findByContest(contest: Long)(implicit session: DBSession = autoSession): List[Image] = withSQL {
    select.from(ImageJdbc as i)
      .where //.append(isNotDeleted)
      // .and
      .eq(i.contest, contest)
      .orderBy(i.pageId)
  }.map(ImageJdbc(i)).list().apply()

  def findByMonumentId(monumentId: String)(implicit session: DBSession = autoSession): List[Image] = withSQL {
    select.from(ImageJdbc as i)
      .where
      .eq(i.monumentId, monumentId)
      .orderBy(i.pageId)
  }.map(ImageJdbc(i)).list().apply()

  def find(id: Long)(implicit session: DBSession = autoSession): Option[Image] = withSQL {
    select.from(ImageJdbc as i).where.eq(i.pageId, id) //.and.append(isNotDeleted)
  }.map(ImageJdbc(i)).single().apply()

  def bySelection(round: Long)(implicit session: DBSession = autoSession): List[Image] = withSQL {
    select.from(ImageJdbc as i)
      .innerJoin(Selection as Selection.s).on(i.pageId, Selection.s.pageId)
      .where.eq(Selection.s.round, round)
  }.map(ImageJdbc(i)).list().apply()

  def bySelectionNotSelected(round: Long)(implicit session: DBSession = autoSession): List[Image] = withSQL {
    select.from(ImageJdbc as i)
      .innerJoin(Selection as Selection.s).on(i.pageId, Selection.s.pageId)
      .where.eq(Selection.s.round, round)
      .and
      .eq(Selection.s.rate, 0)
  }.map(ImageJdbc(i)).list().apply()

  def bySelectionSelected(round: Long)(implicit session: DBSession = autoSession): List[Image] = withSQL {
    select.from(ImageJdbc as i)
      .innerJoin(Selection as Selection.s).on(i.pageId, Selection.s.pageId)
      .where.eq(Selection.s.round, round).and
      .ne(Selection.s.rate, 0)
  }.map(ImageJdbc(i)).list().apply()

  def byUser(user: User, roundId: Long)(implicit session: DBSession = autoSession): Seq[Image] = withSQL {
    select.from(ImageJdbc as i)
      .innerJoin(Selection as Selection.s).on(i.pageId, Selection.s.pageId)
      .where.eq(Selection.s.juryId, user.id).and
      .eq(Selection.s.round, roundId).and
      .append(isNotDeleted)
  }.map(ImageJdbc(i)).list().apply()

  def byUserSelected(user: User, roundId: Long)(implicit session: DBSession = autoSession): Seq[Image] = withSQL {
    select.from(ImageJdbc as i)
      .innerJoin(Selection as Selection.s).on(i.pageId, Selection.s.pageId)
      .where.eq(Selection.s.juryId, user.id).and
      .eq(Selection.s.round, roundId).and
      .ne(Selection.s.rate, 0).and
      .append(isNotDeleted)
  }.map(ImageJdbc(i)).list().apply()

  def findWithSelection(id: Long, roundId: Long)(implicit session: DBSession = autoSession): Seq[ImageWithRating] = withSQL {
    select.from(ImageJdbc as i)
      .innerJoin(Selection as Selection.s).on(i.pageId, Selection.s.pageId)
      .where.eq(i.pageId, id).and
      .eq(Selection.s.round, roundId)
  }.map(rs => (ImageJdbc(i)(rs), Selection(Selection.s)(rs))).list().apply().map { case (i, s) => ImageWithRating(i, Seq(s)) }

  def byUserImageWithRating(user: User, roundId: Long)(implicit session: DBSession = autoSession): Seq[ImageWithRating] = withSQL {
    select.from(ImageJdbc as i)
      .innerJoin(Selection as Selection.s).on(i.pageId, Selection.s.pageId)
      .where.eq(Selection.s.juryId, user.id).and
      .eq(Selection.s.round, roundId)
      .and.append(isNotDeleted)
  }.map(rs => (ImageJdbc(i)(rs), Selection(Selection.s)(rs))).list().apply().map { case (i, s) => ImageWithRating(i, Seq(s)) }

  def byUserImageWithCriteriaRating(user: User, roundId: Long)(implicit session: DBSession = autoSession): Seq[ImageWithRating] = withSQL {
    select(
      sum(CriteriaRate.c.rate), count(CriteriaRate.c.rate),
      i.result.*, Selection.s.result.*)
      .from(ImageJdbc as i)
      .innerJoin(Selection as Selection.s).on(i.pageId, Selection.s.pageId)
      .leftJoin(CriteriaRate as CriteriaRate.c).on(Selection.s.id, CriteriaRate.c.selection)
      .where.eq(Selection.s.juryId, user.id).and
      .eq(Selection.s.round, roundId)
      .and.append(isNotDeleted)
      .groupBy(Selection.s.id)
  }.map(rs => (ImageJdbc(i)(rs), Selection(Selection.s)(rs), rs.intOpt(1).getOrElse(0), rs.intOpt(2).getOrElse(0))).list().apply().map {
    case (img, selection, sum, criterias) =>

      if (criterias > 0)
        ImageWithRating(img, Seq( selection.copy(rate = sum)), criterias)
      else
        ImageWithRating(img, Seq( selection))
    }

  def byRating(roundId: Long, rate: Int)(implicit session: DBSession = autoSession): Seq[ImageWithRating] = withSQL {
    select.from(ImageJdbc as i)
      .innerJoin(Selection as Selection.s).on(i.pageId, Selection.s.pageId)
      .where.eq(Selection.s.rate, rate).and
      .eq(Selection.s.round, roundId)
      .and.append(isNotDeleted)
  }.map(rs => (ImageJdbc(i)(rs), Selection(Selection.s)(rs))).list().apply().map { case (img, s) => ImageWithRating(img, Seq(s)) }

  def byRatingWithCriteria(roundId: Long, rate: Int)(implicit session: DBSession = autoSession): Seq[ImageWithRating] = withSQL {
    select(sum(CriteriaRate.c.rate), count(CriteriaRate.c.rate), i.result.*, Selection.s.result.*).from(ImageJdbc as i)
      .innerJoin(Selection as Selection.s).on(i.pageId, Selection.s.pageId)
      .leftJoin(CriteriaRate as CriteriaRate.c).on(Selection.s.id, CriteriaRate.c.selection)
      .where.eq(Selection.s.rate, rate).and
      .eq(Selection.s.round, roundId)
      .and.append(isNotDeleted)
      .groupBy(Selection.s.id)
  }.map(rs => (ImageJdbc(i)(rs), Selection(Selection.s)(rs), rs.intOpt(1).getOrElse(0), rs.intOpt(2).getOrElse(0))).list().apply()
    .map { case (img, selection, sum, criterias) =>
      if (criterias > 0)
      ImageWithRating(img, Seq( selection.copy(rate = sum)), criterias)
    else
      ImageWithRating(img, Seq( selection))
    }

  def byRatingGE(roundId: Long, rate: Int)(implicit session: DBSession = autoSession): Seq[ImageWithRating] = withSQL {
    select.from(ImageJdbc as i)
      .innerJoin(Selection as Selection.s).on(i.pageId, Selection.s.pageId)
      .where.ge(Selection.s.rate, rate).and
      .eq(Selection.s.round, roundId)
      .and.append(isNotDeleted)
  }.map(rs => (ImageJdbc(i)(rs), Selection(Selection.s)(rs))).list().apply().map { case (i, s) => ImageWithRating(i, Seq(s)) }


  def byRound(roundId: Long)(implicit session: DBSession = autoSession): Seq[ImageWithRating] = withSQL {
    select.from(ImageJdbc as i)
      .innerJoin(Selection as Selection.s).on(i.pageId, Selection.s.pageId)
      .where.eq(Selection.s.round, roundId)
      .and.append(isNotDeleted)
  }.map(rs => (ImageJdbc(i)(rs), Selection(Selection.s)(rs))).list().apply().map { case (i, s) => ImageWithRating(i, Seq(s)) }

  def byRatingMerged(rate: Int, round: Int): Seq[ImageWithRating] = {
    val raw = ImageJdbc.byRating(round, rate)
    val merged = raw.groupBy(_.pageId).mapValues(iws => new ImageWithRating(iws.head.image, iws.map(_.selection.head)))
    merged.values.toSeq
  }

  def byRatingWithCriteriaMerged(rate: Int, round: Int): Seq[ImageWithRating] = {
    val raw = ImageJdbc.byRatingWithCriteria(round, rate)
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

  def byRoundWithCriteriaMerged(round: Int): Seq[ImageWithRating] = {
    val raw = ImageJdbc.byRound(round)
    val merged = raw.groupBy(_.pageId).mapValues(iws => new ImageWithRating(iws.head.image, iws.map(_.selection.head)))
    merged.values.toSeq
  }


  import SQLSyntax.{sum, count}

  def byRoundSummed(roundId: Long)(implicit session: DBSession = autoSession): Seq[ImageWithRating] = withSQL {
    select(sum(Selection.s.rate), count(Selection.s.rate), i.result.*).from(ImageJdbc as i)
      .innerJoin(Selection as Selection.s).on(i.pageId, Selection.s.pageId)
      .where.eq(Selection.s.round, roundId)
      .and.gt(Selection.s.rate, 0)
      .and.append(isNotDeleted).groupBy(Selection.s.pageId)
  }.map(rs => (ImageJdbc(i)(rs), rs.intOpt(1).getOrElse(0), rs.intOpt(2).getOrElse(0))).list().apply().map {
    case (img, sum, count) => ImageWithRating(img, Seq(new Selection(0, img.pageId, sum, 0, roundId)), count)
  }

  def byRoundSummedWithCriteria(roundId: Long)(implicit session: DBSession = autoSession): Seq[ImageWithRating] = withSQL {
    select(sum(Selection.s.rate), count(Selection.s.rate),
      sum(CriteriaRate.c.rate), count(CriteriaRate.c.rate),
      i.result.*)
      .from(ImageJdbc as i)
      .innerJoin(Selection as Selection.s).on(i.pageId, Selection.s.pageId)
      .leftJoin(CriteriaRate as CriteriaRate.c).on(Selection.s.id, CriteriaRate.c.selection)
      .where.eq(Selection.s.round, roundId)
      .and.gt(Selection.s.rate, 0)
      .and.append(isNotDeleted)
      .groupBy(Selection.s.pageId)
  }.map(rs => (ImageJdbc(i)(rs),
    rs.intOpt(1).getOrElse(0),
    rs.intOpt(2).getOrElse(0),
    rs.intOpt(3).getOrElse(0),
    rs.intOpt(4).getOrElse(0))).list().apply().map {
    case (img, ssum, scount, csum, ccount) =>
      if (ccount > 0)
        ImageWithRating(img, Seq(new Selection(0, img.pageId, csum, 0, ccount)), scount)
      else
        ImageWithRating(img, Seq(new Selection(0, img.pageId, ssum, 0, roundId)), scount)

  }


}
