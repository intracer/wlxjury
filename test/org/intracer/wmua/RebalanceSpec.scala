package org.intracer.wmua

import org.intracer.wmua.cmd.DistributeImages
import org.specs2.mutable.Specification
import DistributeImages._

class RebalanceSpec extends Specification {

  val round = new Round(Some(1), number = 1, contest = 1)
  val selection = Selection(1, 1, rate = 0, 1, 1)
  val juror = User("", "")
  val image = Image(1, "")

  "Rebalance" should {

    "do nothing" in {
      rebalanceImages(round, Nil, Nil, Nil) === NoRebalance
      rebalanceImages(round, Seq(selection), Seq(juror), Seq(image)) === NoRebalance
    }
  }

}
