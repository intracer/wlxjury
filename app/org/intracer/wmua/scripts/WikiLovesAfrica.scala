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
      val contestId = contest.id.get

      val cmds = Seq(
        ConnectDb(),
        AddUsers(contestId, "jury", 10, juror)
      )

      cmds.foreach(_.apply())

      val (prevRound, round) = AddNextRound(contestId, roundNumber = 2, distribution = 0, rates = 10).apply()

      val images = Tools.getFilteredImages(round, round.jurors, Some(prevRound), selectedAtLeast = Some(1))
      Tools.distributeImages(round, round.jurors, images)

      SetCurrentRound(contestId, Some(prevRound), round).apply()
    }
  }
}
