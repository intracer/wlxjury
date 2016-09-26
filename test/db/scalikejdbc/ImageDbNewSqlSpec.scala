package db.scalikejdbc

import db.scalikejdbc.rewrite.ImageDbNew.SelectionQuery
import org.intracer.wmua.Selection
import org.specs2.mutable.Specification

class ImageDbNewSqlSpec extends Specification with InMemDb {

  val imageFields = "i.page_id as pi_on_i, i.contest as c_on_i, i.title as t_on_i, i.url as u_on_i, i.page_url as pu_on_i, " +
    "i.last_round as lr_on_i, i.width as w_on_i, i.height as h_on_i, i.monument_id as mi_on_i, i.description as d_on_i"

  val selectionFields = "s.id as i_on_s, s.jury_id as ji_on_s, s.created_at as ca_on_s, s.deleted_at as da_on_s, " +
    "s.round as r1_on_s, s.page_id as pi_on_s, s.rate as r2_on_s, s.criteria_id as ci_on_s"

  val allFields = imageFields + ", " + selectionFields


  def foldSpace(s: String) = s.replaceAll("\\s+", " ").trim

  sequential

  def check(q: SelectionQuery, expected: String) = {
    inMemDbApp {
      foldSpace(q.query()) === foldSpace(expected)
    }
  }


  "list" should {
    "all" in {
      check(SelectionQuery(),
        s"""select $allFields from images i
            join selection s
            on i.page_id = s.page_id"""
      )
    }

    "by user" in {
      check(SelectionQuery(userId = Some(123)),
        s"""select $allFields from images i
            join selection s
            on i.page_id = s.page_id
            where
            s.jury_id = 123"""
      )
    }

    "by round" in {
      check(SelectionQuery(roundId = Some(234)),
        s"""select $allFields from images i
            join selection s
            on i.page_id = s.page_id
            where
            s.round = 234"""
      )
    }

    "by rate" in {
      check(SelectionQuery(rate = Some(1)),
        s"""select $allFields from images i
            join selection s
            on i.page_id = s.page_id
            where
            s.rate = 1"""
      )
    }

    "by user, round, rate" in {
      check(SelectionQuery(userId = Some(2), roundId = Some(3), rate = Some(1)),
        s"""select $allFields from images i
            join selection s
            on i.page_id = s.page_id
            where
            s.jury_id = 2 and s.round = 3 and s.rate = 1"""
      )
    }
  }

  "grouped" should {

    "group by page list by round" in {
      check(SelectionQuery(roundId = Some(3), grouped = true),
        s"""select $imageFields, sum(s.rate) as rate, count(s.rate) as rate_count from images i
            join selection s
            on i.page_id = s.page_id
            where
            s.round = 3
          group by s.page_id"""
      )
    }
  }

}
