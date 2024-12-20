package db.scalikejdbc.rewrite

import _root_.play.api.i18n.Messages
import db.scalikejdbc.{ImageJdbc, SelectionJdbc}
import org.intracer.wmua.{Image, ImageWithRating, Region, Selection}
import org.scalawiki.wlx.dto.Country.Ukraine
import scalikejdbc.{DBSession, _}

object ImageDbNew extends SQLSyntaxSupport[Image] {

  implicit def session: DBSession = autoSession

  override val tableName = "images"

  private val i = ImageJdbc.syntax("i")
  private val s = SelectionJdbc.s
  private val s1 = SelectionJdbc.syntax("s1")

  case class Limit(
      pageSize: Option[Int] = None,
      offset: Option[Int] = None,
      startPageId: Option[Long] = None
  )

  case class SelectionQuery(
      userId: Option[Long] = None,
      roundId: Option[Long] = None,
      rate: Option[Int] = None,
      rated: Option[Boolean] = None,
      regions: Set[String] = Set.empty,
      limit: Option[Limit] = None,
      grouped: Boolean = false,
      groupWithDetails: Boolean = false,
      order: Map[String, Int] = Map.empty,
      subRegions: Boolean = false,
      withPageId: Option[Long] = None,
      driver: String = "mysql"
  ) {

    private val reader: WrappedResultSet => ImageWithRating =
      if (grouped) Readers.groupedReader else Readers.rowReader

    private val regionColumn = if (subRegions) "adm1" else "adm0"

    def query(
        count: Boolean = false,
        idOnly: Boolean = false,
        noLimit: Boolean = false,
        byRegion: Boolean = false,
        ranked: Boolean = false
    ): String = {

      val columns: String = "select  " +
        (if (count || idOnly) {
           "i.page_id as pi_on_i" +
             (if (ranked)
                s", ROW_NUMBER() over (${orderBy()}) ranked"
              else "") +
             (if (grouped)
                ", sum(s.rate) as rate, count(s.rate) as rate_count"
              else "")
         } else {
           if (byRegion)
             s"m.$regionColumn, count(DISTINCT i.page_id)"
           else if (!grouped)
             sqls"""${i.result.*}, ${s.result.*} """.value
           else
             sqls"""sum(s.rate) as rate, count(s.rate) as rate_count, ${i.result.*} """.value
         })

      val groupBy = if (byRegion) {
        s" group by m.$regionColumn"
      } else if (grouped) {
        " group by s.page_id"
      } else ""

      val sql = columns + join(monuments = regions.size > 1 || byRegion) +
        where(count) +
        groupBy +
        (if (!(count || byRegion)) orderBy() else "")

      val result =
        if (count)
          "select count(t.pi_on_i) from (" + sql + ") t"
        else
          sql + (if (noLimit || byRegion) "" else limitSql())

      result
    }

    def list(): Seq[ImageWithRating] = {
      postProcessor(SQL(query()).map(reader).list())
    }

    def count(): Int = {
      single(query(count = true))
    }

    def imageRank(pageId: Long): Int = {
      single(
        imageRankSql(
          pageId,
          query(ranked = true, idOnly = true, noLimit = true)
        )
      )
    }

    def byRegionStat()(implicit messages: Messages): Seq[Region] = {
      val map = SQL(s"""select distinct substring(i.monument_id, 1, 2)
                       |from images i
                       |         join selection s on i.page_id = s.page_id
                       |              where ${userId.fold("") { id => s"s.jury_id = $id and " }}
                       |                s.round_id = ${roundId.get}""".stripMargin)
        .map(rs => rs.string(1) -> None)
        .list()
        .toMap
      regions(map, subRegions)
    }

    def regions(byRegion: Map[String, Option[Int]], subRegions: Boolean = false)(implicit
        messages: Messages
    ): Seq[Region] = {
      val regions = byRegion.keys
        .filterNot(_ == null)
        .map { id =>
          val adm = Ukraine.byMonumentId(id)
          val name =
            if (messages.isDefinedAt(id)) messages(id)
            else adm.map(_.name).getOrElse("Unknown")
          Region(id, name, byRegion(id))
        }
        .toSeq
        .sortBy(_.id)

      if (subRegions) {
        val kyivPictures =
          regions.filter(_.id.startsWith("80-")).map(_.count.getOrElse(0)).sum
        val withoutKyivRegions = regions.filterNot(_.id.startsWith("80-"))
        val unsorted =
          withoutKyivRegions ++ Seq(Region("80", messages("80"), Some(kyivPictures)))
        unsorted.sortBy(_.name)
      } else {
        regions.sortBy(_.id)
      }
    }

    def single(sql: String): Int = {
      SQL(sql).map(_.int(1)).single().getOrElse(0)
    }

    private val imagesJoinSelection =
      """ from images i
        |join selection s
        |on i.page_id = s.page_id""".stripMargin

    def join(monuments: Boolean): String = {
      imagesJoinSelection + (if (monuments)
                               "\n join monument m on i.monument_id = m.id"
                             else "")
    }

    def where(count: Boolean = false): String = {
      val conditions =
        Seq(
          userId.map(id => "s.jury_id = " + id),
          roundId.map(id => "s.round_id = " + id),
          rate.map(r => "s.rate = " + r),
          rated.map { r =>
            val rated = if (r) "s.rate > 0" else "s.rate = 0"
            withPageId.fold(rated) { pageId =>
              s"($rated or s.page_id = $pageId)"
            }
          },
          regions.headOption.map { _ =>
            if (regions.headOption.exists(_.length > 2)) {
              s"m.$regionColumn in (" + regions
                .map(r => s"'$r'")
                .mkString(", ") + ")"
            } else {
              if (regions.size > 1) {
                s"m.adm0 in (" + regions.map(r => s"'$r'").mkString(", ") + ")"
              } else {
                s"i.monument_id like '${regions.head}%'"
              }
            }
          }
        ).flatten

      conditions.headOption.fold("") { _ =>
        " where " + conditions.mkString(" and ")
      }
    }

    def orderBy(fields: Map[String, Int] = order): String = {
      val dirMap = Map(1 -> "asc", -1 -> "desc")

      fields.headOption
        .map { _ =>
          " order by " + fields
            .map { case (name, dir) =>
              name + " " + dirMap(dir)
            }
            .mkString(", ")
        }
        .getOrElse("")
    }

    def limitSql(): String = limit
      .map { l =>
        s" LIMIT ${l.pageSize.getOrElse(0)} OFFSET ${l.offset.getOrElse(0)}"
      }
      .getOrElse("")

    val postProcessor: Seq[ImageWithRating] => Seq[ImageWithRating] =
      if (groupWithDetails) groupedWithDetails else identity

    def groupedWithDetails(images: Seq[ImageWithRating]): Seq[ImageWithRating] =
      images
        .groupBy(_.image.pageId)
        .map { case (id, imagesWithId) =>
          new ImageWithRating(
            imagesWithId.head.image,
            imagesWithId.flatMap(_.selection)
          )
        }
        .toSeq
        .sortBy(-_.selection.map(_.rate).filter(_ > 0).sum)

    def imageRankSql(pageId: Long, sql: String): String = {
      val result = if (driver == "mysql") {
        s"""SELECT ranked, pi_on_i
            FROM ($sql) t
            WHERE pi_on_i = $pageId;"""
      } else {
        s"""SELECT rank FROM
            (SELECT rownum as rank, t.pi_on_i as page_id
            FROM  ($sql) t) t2
        WHERE page_id = $pageId;"""
      }
      result
    }

    def rankedList(where: String): Seq[ImageWithRating] = {
      SQL(
        s"""SELECT count(s2.page_id) + 1 AS rank, ${i.result.*}, ${s1.result.*}
    FROM images i
    JOIN (SELECT * FROM selection s WHERE $where) AS s1
    ON i.page_id = s1.page_id
    LEFT JOIN (SELECT * FROM selection s WHERE s.jury_id = $userId AND s.round_id = $roundId) AS s2
    ON s1.rate < s2.rate
    GROUP BY s1.page_id
    ORDER BY rank ASC
    $limit"""
      ).map(rs => (rs.int(1), ImageJdbc(i)(rs), SelectionJdbc(s1)(rs)))
        .list()
        .map { case (rank, img, sel) =>
          ImageWithRating(img, Seq(sel), rank = Some(rank))
        }
    }

    def rangeRankedList(where: String): Seq[ImageWithRating] = {
      SQL(s"""SELECT s1.rank1, s2.rank2, ${i.result.*}, ${s1.result.*}
          FROM images i JOIN
            (SELECT t1.*, count(t2.page_id) + 1 AS rank1
            FROM (SELECT * FROM selection s WHERE  $where) AS t1
            LEFT JOIN (SELECT * FROM selection s WHERE $where) AS t2
              ON  t1.rate < t2.rate
          GROUP BY t1.page_id) s1
              ON  i.page_id = s1.page_id
          JOIN
              (SELECT t1.page_id, count(t2.page_id) AS rank2
                 FROM (SELECT * FROM selection s WHERE $where) AS t1
                 JOIN (SELECT * FROM selection s WHERE $where) AS t2
                   ON  t1.rate <= t2.rate
               GROUP BY t1.page_id) s2
            ON s1.page_id = s2.page_id
            ORDER BY rank1 ASC $limit()""")
        .map(rs => (rs.int(1), rs.int(2), ImageJdbc(i)(rs), SelectionJdbc(s1)(rs)))
        .list()
        .map { case (rank1, rank2, i, s) =>
          ImageWithRating(i, Seq(s), rank = Some(rank1), rank2 = Some(rank2))
        }
    }

    object Readers {

      def rowReader(rs: WrappedResultSet): ImageWithRating =
        ImageWithRating(
          image = ImageJdbc(i)(rs),
          selection = Seq(SelectionJdbc(s)(rs))
        )

      def groupedReader(rs: WrappedResultSet): ImageWithRating = {
        val image = ImageJdbc(i)(rs)
        val sum = rs.intOpt(1).getOrElse(0)
        val count = rs.intOpt(2).getOrElse(0)
        ImageWithRating(
          image,
          selection = Seq(Selection(image.pageId, juryId = 0, roundId = 0, rate = sum)),
          count
        )
      }

      def regionStatReader(rs: WrappedResultSet): (String, Int) = {
        rs.string(1) -> rs.int(2)
      }
    }

  }

}
