package org.intracer.wmua.scripts

import db.scalikejdbc.ContestJuryJdbc
import org.intracer.wmua.Tools
import org.intracer.wmua.cmd._

object WikiLovesAfrica {

  val contestType = "Wiki Loves Africa"
  val country = "Africa"
  val year = 2015

  def juror(country: String, n: Int) = "WLAJuror" + n

  def main(args: Array[String]) {
    for (contest <- ContestJuryJdbc.find(contestType, country, year)) {
      val contestId = contest.id.get

      val cmds = Seq(
        ConnectDb(),
        AddUsers(contestId, "jury", 10, juror)
      )

      cmds.foreach(_.apply())

      val (prevRound, round) = AddNextRound(contestId, roundNumber = 2, distribution = 0, rates = 10).apply()

      Tools.distributeImagesWithFilters(round, round.jurors, Some(prevRound), selectedAtLeast = Some(1))

      SetCurrentRound(contestId, Some(prevRound), round).apply()
    }
  }
}
