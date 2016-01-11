package org.intracer.wmua.scripts

import org.intracer.wmua.Tools
import org.intracer.wmua.cmd._

object WikiLovesAfrica {

  def juror(country: String, n: Int) = "WLAJuror" + n

  def main(args: Array[String]) {
    val contestId = 76L

    val cmds = Seq(
      ConnectDb(),
      AddUsers(contestId, "jury", 10, juror)
    )

    cmds.foreach(_.apply())

    val (prevRound, round) = AddNextRound(contestId, roundNumber = 2, distribution = 0, rates = 10).apply()

    Tools.distributeImages(round, round.jurors, Some(prevRound), selectedAtLeast = Some(1))

    SetCurrentRound(contestId, Some(prevRound), round).apply()
  }
}
