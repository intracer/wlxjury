package org.intracer.wmua

import org.specs2.mutable.Specification

class RankSpec extends Specification {

  import ImageWithRating.rank

  "rank" should {

    "rank natural sequence" in {
      rank(Seq(3, 2, 1)) === Seq("1", "2", "3")
    }

    "rank sparse sequence" in {
      rank(Seq(10, 5, 1)) === Seq("1", "2", "3")
    }

    "rank ties" in {
      rank(Seq(10, 5, 5, 1)) === Seq("1", "2-3", "2-3", "4")
    }
  }
}
