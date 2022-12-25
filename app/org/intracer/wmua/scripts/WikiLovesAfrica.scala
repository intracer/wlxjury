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
    for (contest <- ContestJuryJdbc.where('name -> contestType, 'country -> country, 'year -> year).apply().headOption) {
      val contestId = contest.getId

      val cmds = Seq(
        ConnectDb("", null),
        AddUsers(contestId, "jury", 10, juror)
      )

      cmds.foreach(_.apply())

      val (prevRound, round) = AddNextRound(contestId, roundNumber = 2, distribution = 0, rates = 10).apply()

      val images = DistributeImages.getFilteredImages(round, round.availableJurors, Some(prevRound), selectedAtLeast = Some(1))
      DistributeImages.distributeImages(round, round.availableJurors, images)

      SetCurrentRound(contestId, Some(prevRound), round).apply()
    }
  }
}
