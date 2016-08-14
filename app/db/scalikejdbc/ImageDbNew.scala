package db.scalikejdbc

import org.intracer.wmua.{Image, ImageWithRating}
import scalikejdbc.{DBSession, _}

object ImageDbNew extends SQLSyntaxSupport[Image] {

  implicit def session: DBSession = autoSession

  //  private def isNotDeleted = sqls.isNull(SelectionJdbc.s.deletedAt)

  override val tableName = "images"

  val i = ImageJdbc.syntax("i")
  val s = SelectionJdbc.s
  val s1 = SelectionJdbc.syntax("s1")
  val s2 = SelectionJdbc.syntax("s2")
  val s3 = SelectionJdbc.syntax("s3")


  case class Limit(pageSize: Option[Int] = None, offset: Option[Int] = None)

  case class Query(
                    userId: Option[Long] = None,
                    roundId: Option[Long] = None,
                    rate: Option[Int] = None,
                    rated: Option[Boolean] = None,
                    limit: Option[Limit]
                  ) {
    val imagesJoinSelection = """from images i join selection s on i.pageId = s.pageId"""

    def where() = {
      val conditions =
        Seq(
          userId.map(Query.user),
          roundId.map(Query.user),
          rate.map(Query.rate),
          rate.map(Query.rate),
          rated.map(Query.rated)
        )

      "where " + conditions.flatten.mkString(" and ")
    }

    def orderBy(fields: Map[String, Int]) = {
      val dirMap = Map(1 -> "asc", -1 -> "desc")

      "order by " + fields.map {
        case (name, dir) => name + " " + dirMap(dir)
      }.mkString(", ")
    }

    def limit() = limit.map {
      l => s"LIMIT ${l.pageSize} OFFSET ${l.offset}"
    }.getOrElse("")

    def list(): Seq[ImageWithRating] = {

      val select = "select ${i.result.*}, ${s.result.*}"

      val q = Seq(select, imagesJoinSelection, where()).mkString("\n")


      sql"$q".map(rs => (
        ImageJdbc(i)(rs),
        SelectionJdbc(s)(rs))
      ).list().apply().map {
        case (img, sel) => ImageWithRating(img, Seq(sel))
      }
    }

    def count(): Int = {
      val select = "select count(i.page_id)"

      val q = Seq(select, imagesJoinSelection, where()).mkString("\n")

      sql"$q".map(rs => rs.int(1)).single().apply().getOrElse(0)
    }

    def imageRank(pageId: Long, sql: String) = {
      sql"""SELECT rank
            FROM (
                  SELECT @rownum :=@rownum + 1 'rank', page_id
                  FROM (SELECT @rownum := 0) r,
                  ($sql) t
                  ) t2
            WHERE page_id = $pageId;"""
    }

    def rankedList(where: String): Seq[ImageWithRating] =
      sql"""SELECT count(s2.page_id) + 1 AS rank, ${i.result.*}, ${s1.result.*}
    FROM images i
    JOIN (SELECT * FROM selection s WHERE $where) AS s1
    ON i.page_id = s1.page_id
    LEFT JOIN (SELECT * FROM selection s WHERE s.jury_id = $userId AND s.round = $roundId) AS s2
    ON s1.rate < s2.rate
    GROUP BY s1.page_id
    ORDER BY rank ASC
    $limit()""".map(rs => (
        rs.int(1),
        ImageJdbc(i)(rs),
        SelectionJdbc(s1)(rs))
      ).list().apply().map {
        case (rank, img, sel) => ImageWithRating(img, Seq(sel), rank = Some(rank))
      }
  }

  def rangeRankedList(where: String): Seq[ImageWithRating] = {
    sql"""SELECT s1.rank1, s2.rank2, ${i.result.*}, ${s1.result.*}
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
            ORDER BY rank1 ASC $limit()""".map(rs => (
      rs.int(1),
      rs.int(2),
      ImageJdbc(i)(rs),
      SelectionJdbc(s1)(rs))
    ).list().apply().map {
      case (rank1, rank2, i, s) => ImageWithRating(i, Seq(s), rank = Some(rank1), rank2 = Some(rank2))
    }
  }

  object Query {
    def user(userId: Long) = s"s.jury_id = $userId"

    def rate(rate: Int) = s"s.rate = $rate"

    def rated(r: Boolean) = "s.rate > 0"
  }

}


