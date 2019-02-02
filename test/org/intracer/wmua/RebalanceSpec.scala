package org.intracer.wmua

import org.intracer.wmua.cmd.DistributeImages
import org.specs2.mutable.Specification
import DistributeImages._

class RebalanceSpec extends Specification {

  val round = new Round(Some(1), number = 1, contestId = 1)
  val selection = Selection(1, 1, 1)
  val juror = User("", "", Some(1))
  val image = Image(1, "")

  "Rebalance" should {

    "do nothing" in {
      rebalanceImages(round, Nil, Nil, Nil) === NoRebalance
      rebalanceImages(round, Seq(juror), Seq(image), Seq(selection)) === NoRebalance
    }

    "init one juror" in {
      rebalanceImages(round, Seq(juror), Seq(image), Nil) === Rebalance(Seq(selection), Nil)
    }
  }

}
