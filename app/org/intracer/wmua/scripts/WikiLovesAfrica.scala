package org.intracer.wmua.scripts

import db.scalikejdbc.{ContestJuryJdbc, Round}
import org.intracer.wmua.Tools
import org.intracer.wmua.cmd._
import services.RoundService

object WikiLovesAfrica {

  val contestType = "Wiki Loves Africa"
  val country = "Africa"
  val year = 2015

  def juror(country: String, n: Int): String = "WLAJuror" + n

  def main(args: Array[String]): Unit = {
    for (contest <- ContestJuryJdbc.where(Symbol("name") -> contestType, Symbol("country") -> country, Symbol("year") -> year).apply().headOption) {
      val contestId = contest.getId

      val cmds = Seq(
        ConnectDb("", null),
        AddUsers(contestId, "jury", 10, juror)
      )

      cmds.foreach(_.apply())

      val (prevRound, round) = AddNextRound(contestId, roundNumber = 2, distribution = 0, rates = 10).apply()

      val images = DistributeImages.getFilteredImages(round, round.availableJurors, Some(prevRound), selectedAtLeast = Some(1))
      DistributeImages.distributeImages(round, round.availableJurors, images)

      new RoundService(Round).setCurrentRound(prevRound.id, round)
    }
  }
}
