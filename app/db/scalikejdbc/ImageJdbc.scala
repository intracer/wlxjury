package db.scalikejdbc

import db.ImageDao
import org.intracer.wmua._
import org.scalawiki.dto.Page
import scalikejdbc._
import scalikejdbc.interpolation.SQLSyntax._

object ImageJdbc extends SQLSyntaxSupport[Image] with ImageDao {

  implicit def session: DBSession = autoSession

  //  private def isNotDeleted = sqls.isNull(SelectionJdbc.s.deletedAt)

  override val tableName = "images"

  val i = ImageJdbc.syntax("i")
  val s = SelectionJdbc.s
  val s1 = SelectionJdbc.syntax("s1")
  val s2 = SelectionJdbc.syntax("s2")
  val s3 = SelectionJdbc.syntax("s3")
  val c = CriteriaRate.c

  import SQLSyntax.{sum, count}

  def fromPage(page: Page, contest: ContestJury): Option[Image] = {
    try {
      for (imageInfo <- page.images.headOption)
        yield new Image(page.id.get, contest.id.get, page.title, imageInfo.url.get, imageInfo.pageUrl.get, imageInfo.width.get, imageInfo.height.get, None)
    } catch {
      case e: Throwable =>
        println(e)
        throw e
    }
  }

  def apply(c: SyntaxProvider[Image])(rs: WrappedResultSet): Image = apply(c.resultName)(rs)

  def apply(c: ResultName[Image])(rs: WrappedResultSet): Image = new Image(
    pageId = rs.long(c.pageId),
    contest = rs.long(c.contest),
    title = rs.string(c.title),
    url = rs.string(c.url),
    pageUrl = rs.string(c.pageUrl),
    width = rs.int(c.width),
    height = rs.int(c.height),
    monumentId = rs.stringOpt(c.monumentId),
    description = rs.stringOpt(c.description)
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
        i.monumentId,
        i.description
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
          column.monumentId -> sqls.?,
          column.description -> sqls.?
        )
      }.batch(batchParams: _*).apply()
    }
  }

  def updateResolution(pageId: Long, width: Int, height: Int): Unit = withSQL {
    update(ImageJdbc).set(
      column.width -> width,
      column.height -> height
    ).where.eq(column.pageId, pageId)
  }.update().apply()

  def updateMonumentId(pageId: Long, monumentId: String): Unit = withSQL {
    update(ImageJdbc).set(
      column.monumentId -> monumentId
    ).where.eq(column.pageId, pageId)
  }.update().apply()

  def deleteImage(pageId: Long): Unit = withSQL {
    delete.from(ImageJdbc as i).where.eq(i.pageId, pageId)
  }.update().apply()

  def findAll(): List[Image] = withSQL {
    select.from(ImageJdbc as i)
      //      .where.append(isNotDeleted)
      .orderBy(i.pageId)
  }.map(ImageJdbc(i)).list().apply()

  def findByContest(contest: Long): List[Image] = withSQL {
    select.from(ImageJdbc as i)
      .where //.append(isNotDeleted)  // .and
      .eq(i.contest, contest)
      .orderBy(i.pageId)
  }.map(ImageJdbc(i)).list().apply()

  def countByContest(contest: Long): Int = withSQL {
    select(count(distinct(ImageJdbc.i.pageId))).from(ImageJdbc as i)
      .where
      .eq(i.contest, contest)
  }.map(_.int(1)).single().apply().getOrElse(0)

  def findByMonumentId(monumentId: String): List[Image] = withSQL {
    select.from(ImageJdbc as i)
      .where
      .eq(i.monumentId, monumentId)
      .orderBy(i.pageId)
  }.map(ImageJdbc(i)).list().apply()

  def find(id: Long): Option[Image] = withSQL {
    select.from(ImageJdbc as i).where.eq(i.pageId, id) //.and.append(isNotDeleted)
  }.map(ImageJdbc(i)).single().apply()


  def rateDistribution(userId: Long, roundId: Long): Map[Int, Int] =
    sql"""SELECT s.rate, count(1)
  FROM images i
  JOIN selection s ON i.page_id = s.page_id
  WHERE
  s.jury_id = $userId
  AND s.round = $roundId
  GROUP BY rate""".map(rs => rs.int(1) -> rs.int(2)).list().apply().toMap


  def byUserRoundRateParamCount(userId: Long, roundId: Long, rate: Int): Int = withSQL {
    select(count(i.pageId))
      .from(ImageJdbc as i)
      .innerJoin(SelectionJdbc as s)
      .on(i.pageId, s.pageId)
      .where.eq(s.juryId, userId).and
      .eq(s.round, roundId).and
      .eq(s.rate, rate)
  }.map(rs => rs.int(1)).single().apply().getOrElse(0)

  def byUserRoundRatedCount(userId: Long, roundId: Long): Int = withSQL {
    select(count(i.pageId))
      .from(ImageJdbc as i)
      .innerJoin(SelectionJdbc as s)
      .on(i.pageId, s.pageId)
      .where.eq(s.juryId, userId).and
      .eq(s.round, roundId).and
      .gt(s.rate, 0)
  }.map(rs => rs.int(1)).single().apply().getOrElse(0)

  def byUserRoundAllCount(userId: Long, roundId: Long): Int = withSQL {
    select(count(i.pageId))
      .from(ImageJdbc as i)
      .innerJoin(SelectionJdbc as s)
      .on(i.pageId, s.pageId)
      .where.eq(s.juryId, userId).and
      .eq(s.round, roundId)
  }.map(rs => rs.int(1)).single().apply().getOrElse(0)

  def byUserImageWithRating(
                             userId: Long,
                             roundId: Long,
                             rate: Option[Int] = None,
                             pageSize: Int = Int.MaxValue,
                             offset: Int = 0
                           ): Seq[ImageWithRating] = withSQL {
    select.from[Image](ImageJdbc as i)
      .innerJoin(SelectionJdbc as s)
      .on(i.pageId, s.pageId)
      .where.eq(s.juryId, userId).and
      .eq(s.round, roundId)
      .map(sql => if (rate.isDefined) sql.and.eq(s.rate, rate) else sql)
      .orderBy(i.pageId).asc
      .limit(pageSize)
      .offset(offset)
  }.map(rs => (
    ImageJdbc(i)(rs),
    SelectionJdbc(SelectionJdbc.s)(rs))
  ).list().apply().map {
    case (i, s) => ImageWithRating(i, Seq(s))
  }

  def byUserImageWithRatingRanked(
                                   userId: Long,
                                   roundId: Long,
                                   pageSize: Int = Int.MaxValue,
                                   offset: Int = 0
                                 ): Seq[ImageWithRating] =

    sql"""SELECT count(s2.page_id) + 1 AS rank, ${i.result.*}, ${s1.result.*}
    FROM images i
    JOIN (SELECT * FROM selection s WHERE s.jury_id = $userId  AND s.round = $roundId) AS s1
    ON i.page_id = s1.page_id
    LEFT JOIN (SELECT * FROM selection s WHERE s.jury_id = $userId AND s.round = $roundId) AS s2
    ON s1.rate < s2.rate
    GROUP BY s1.page_id
    ORDER BY rank ASC
    LIMIT $pageSize
    OFFSET $offset""".map(rs => (
      rs.int(1),
      ImageJdbc(i)(rs),
      SelectionJdbc(s1)(rs))
    ).list().apply().map {
      case (rank, i, s) => ImageWithRating(i, Seq(s), rank = Some(rank))
    }

  def byUserImageRangeRanked(userId: Long,
                             roundId: Long,
                             pageSize: Int = Int.MaxValue,
                             offset: Int = 0
                            ): Seq[ImageWithRating] =

    sql"""SELECT s1.rank1, s2.rank2, ${i.result.*}, ${s1.result.*}
          FROM images i JOIN
            (SELECT t1.*, count(t2.page_id) + 1 AS rank1
            FROM (SELECT * FROM selection s WHERE  s.jury_id = $userId AND s.round = $roundId) AS t1
            LEFT JOIN (SELECT * FROM selection s WHERE s.jury_id = $userId AND s.round = $roundId) AS t2
              ON  t1.rate < t2.rate
          GROUP BY t1.page_id) s1
              ON  i.page_id = s1.page_id
          JOIN
              (SELECT t1.page_id, count(t2.page_id) AS rank2
                 FROM (SELECT * FROM selection s WHERE  s.jury_id = $userId AND s.round = $roundId) AS t1
                 JOIN (SELECT * FROM selection s WHERE s.jury_id = $userId AND s.round = $roundId) AS t2
                   ON  t1.rate <= t2.rate
               GROUP BY t1.page_id) s2
            ON s1.page_id = s2.page_id
            ORDER BY rank1 ASC
          LIMIT $pageSize
          OFFSET $offset""".map(rs => (
      rs.int(1),
      rs.int(2),
      ImageJdbc(i)(rs),
      SelectionJdbc(s1)(rs))
    ).list().apply().map {
      case (rank1, rank2, i, s) => ImageWithRating(i, Seq(s), rank = Some(rank1), rank2 = Some(rank2))
    }

  def findImageWithRating = withSQL {
    select.from(ImageJdbc as i)
      .innerJoin(SelectionJdbc as s)
      .on(i.pageId, s.pageId)
  }.map(rs => (
    ImageJdbc(i)(rs),
    SelectionJdbc(SelectionJdbc.s)(rs))
  ).list().apply().map {
    case (i, s) => ImageWithRating(i, Seq(s))
  }

  def byUserImageWithCriteriaRating(userId: Long, roundId: Long): Seq[ImageWithRating] = withSQL {
    select(
      sum(c.rate), count(c.rate),
      i.result.*, s.result.*)
      .from(ImageJdbc as i)
      .innerJoin(SelectionJdbc as s).on(i.pageId, s.pageId)
      .leftJoin(CriteriaRate as c).on(s.id, c.selection)
      .where.eq(s.juryId, userId).and
      .eq(s.round, roundId)
      //      .and.append(isNotDeleted)
      .groupBy(s.id)
  }.map(rs => (ImageJdbc(i)(rs), SelectionJdbc(s)(rs), rs.intOpt(1).getOrElse(0), rs.intOpt(2).getOrElse(0))).list().apply().map {
    case (img, selection, sum, criterias) =>

      if (criterias > 0)
        ImageWithRating(img, Seq(selection.copy(rate = sum)), criterias)
      else
        ImageWithRating(img, Seq(selection))
  }

  def byRating(roundId: Long, rate: Int): Seq[ImageWithRating] = withSQL {
    select.from(ImageJdbc as i)
      .innerJoin(SelectionJdbc as s).on(i.pageId, s.pageId)
      .where.eq(s.rate, rate).and
      .eq(s.round, roundId)
    //      .and.append(isNotDeleted)
  }.map(rs => (ImageJdbc(i)(rs), SelectionJdbc(s)(rs))).list().apply().map { case (img, s) => ImageWithRating(img, Seq(s)) }

  def byRatingWithCriteria(roundId: Long, rate: Int): Seq[ImageWithRating] = withSQL {
    select(sum(c.rate), count(c.rate), i.result.*, s.result.*).from(ImageJdbc as i)
      .innerJoin(SelectionJdbc as s).on(i.pageId, s.pageId)
      .leftJoin(CriteriaRate as c).on(s.id, c.selection)
      .where.eq(s.rate, rate).and
      .eq(s.round, roundId)
      //      .and.append(isNotDeleted)
      .groupBy(s.id)
  }.map(rs => (ImageJdbc(i)(rs), SelectionJdbc(s)(rs), rs.intOpt(1).getOrElse(0), rs.intOpt(2).getOrElse(0))).list().apply()
    .map { case (img, selection, sum, criterias) =>
      if (criterias > 0)
        ImageWithRating(img, Seq(selection.copy(rate = sum)), criterias)
      else
        ImageWithRating(img, Seq(selection))
    }

  def byRatingGE(roundId: Long, rate: Int): Seq[ImageWithRating] = withSQL {
    select.from(ImageJdbc as i)
      .innerJoin(SelectionJdbc as s).on(i.pageId, s.pageId)
      .where.ge(s.rate, rate).and
      .eq(s.round, roundId)
    //      .and.append(isNotDeleted)
  }.map(rs => (ImageJdbc(i)(rs), SelectionJdbc(s)(rs))).list().apply().map { case (i, s) => ImageWithRating(i, Seq(s)) }


  def byRound(roundId: Long): Seq[ImageWithRating] = withSQL {
    select.from(ImageJdbc as i)
      .innerJoin(SelectionJdbc as s).on(i.pageId, s.pageId)
      .where.eq(s.round, roundId)
    //      .and.append(isNotDeleted)
  }.map(rs => (ImageJdbc(i)(rs), SelectionJdbc(s)(rs))).list().apply().map { case (i, s) => ImageWithRating(i, Seq(s)) }

  def byRatingMerged(rate: Int, round: Long): Seq[ImageWithRating] = {
    val raw = ImageJdbc.byRating(round, rate)
    val merged = raw.groupBy(_.pageId).mapValues(iws => new ImageWithRating(iws.head.image, iws.map(_.selection.head)))
    merged.values.toSeq
  }

  def byRatingWithCriteriaMerged(rate: Int, round: Long): Seq[ImageWithRating] = {
    val raw = ImageJdbc.byRatingWithCriteria(round, rate)
    val merged = raw.groupBy(_.pageId).mapValues(iws => new ImageWithRating(iws.head.image, iws.map(_.selection.head)))
    merged.values.toSeq
  }

  def byRatingGEMerged(rate: Int, round: Long): Seq[ImageWithRating] = {
    val raw = ImageJdbc.byRatingGE(round, rate)
    val merged = raw.groupBy(_.pageId).mapValues(iws => new ImageWithRating(iws.head.image, iws.map(_.selection.head)))
    merged.values.toSeq
  }

  def byRoundMerged(round: Long): Seq[ImageWithRating] = {
    val raw = ImageJdbc.byRound(round)
    val merged = raw.groupBy(_.pageId).mapValues(iws => new ImageWithRating(iws.head.image, iws.map(_.selection.head)))
    merged.values.toSeq
  }

  def byRoundWithCriteriaMerged(round: Int): Seq[ImageWithRating] = {
    val raw = ImageJdbc.byRound(round)
    val merged = raw.groupBy(_.pageId).mapValues(iws => new ImageWithRating(iws.head.image, iws.map(_.selection.head)))
    merged.values.toSeq
  }


  def byRoundSummed(roundId: Long): Seq[ImageWithRating] = withSQL {
    select(sum(s.rate), count(s.rate), i.result.*).from(ImageJdbc as i)
      .innerJoin(SelectionJdbc as s).on(i.pageId, s.pageId)
      .where.eq(s.round, roundId)
      .and.ge(s.rate, 0)
      //.and.append(isNotDeleted)
      .groupBy(s.pageId)
  }.map(rs => (ImageJdbc(i)(rs), rs.intOpt(1).getOrElse(0), rs.intOpt(2).getOrElse(0))).list().apply().map {
    case (img, sum, count) => ImageWithRating(img, Seq(new Selection(0, img.pageId, sum, 0, roundId)), count)
  }

  def byRoundSummedWithCriteria(roundId: Long): Seq[ImageWithRating] = withSQL {
    select(sum(s.rate), count(s.rate),
      sum(c.rate), count(c.rate),
      i.result.*)
      .from(ImageJdbc as i)
      .innerJoin(SelectionJdbc as s).on(i.pageId, s.pageId)
      .leftJoin(CriteriaRate as CriteriaRate.c).on(s.id, c.selection)
      .where.eq(s.round, roundId)
      .and.ge(s.rate, 0)
      //      .and.append(isNotDeleted)
      .groupBy(s.pageId)
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
