package db.scalikejdbc

import db.scalikejdbc.rewrite.ImageDbNew.SelectionQuery
import org.specs2.mutable.Specification

class SelectionQuerySpec extends Specification {

  "where" should {
    "no conditions" in {
      SelectionQuery().where() === ""
    }

    "userId" in {
      val userId = 1L
      SelectionQuery(userId = Some(userId))
        .where() === s" where s.jury_id = $userId"
    }

    "roundId" in {
      val roundId = 2L
      SelectionQuery(roundId = Some(roundId))
        .where() === s" where s.round_id = $roundId"
    }

    "rate" in {
      val rate = 1
      SelectionQuery(rate = Some(rate))
        .where() === s" where s.rate = $rate"
    }

    "rated true" in {
      SelectionQuery(rated = Some(true))
        .where() === s" where s.rate > 0"
    }

    "rated false" in {
      SelectionQuery(rated = Some(false))
        .where() === s" where s.rate = 0"
    }

  }

}
