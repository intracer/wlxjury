package org.intracer.wmua.scripts

import db.scalikejdbc.{ContestJuryJdbc, RoundJdbc}
import org.intracer.wmua.{Round, Tools}
import org.intracer.wmua.cmd.{AddRound, AddUsers, ConnectDb}

object WikiLovesAfrica {

  def juror(country: String, n: Int) = "WLAJuror" + n

  def main(args: Array[String]) {
    val contestId = 76L

    val cmds = Seq(
      ConnectDb(),
      AddUsers(76L, "jury", 10, juror)
    )

    cmds.foreach(_.apply())

    val (prevRound, round) = addNextRound(contestId, roundNumber = 2, distribution = 0, rates = 10)

    Tools.distributeImages(round, round.jurors, prevRound, selectedAtLeast = Some(1))

    setCurrentRound(contestId, prevRound, round)
  }

  def setCurrentRound(contestId: Long, prevRound: Round, round: Round) = {
    println(s"Setting current round from ${prevRound.id.get} to ${round.id.get}")

    ContestJuryJdbc.setCurrentRound(contestId, round.id.get)
    RoundJdbc.setActive(prevRound.id.get, active = false)
    RoundJdbc.setActive(round.id.get, active = true)
  }

  def addNextRound(contestId: Long, roundNumber: Int, distribution: Int = 0, rates: Int = 1, name: Option[String] = None): (Round, Round) = {
    val rounds = RoundJdbc.findByContest(contestId)
    val prevRound = rounds.last
    if (prevRound.number >= roundNumber) {
      println(s"last round has number ${prevRound.number}, not going to create round number $roundNumber")

      (rounds.find(_.number == roundNumber - 1).get, rounds.find(_.number == roundNumber).get)
    }  else {
      val newRound = AddRound(contestId, prevRound.number + 1, distribution, rates).apply()
      println(s"last round has number ${prevRound.number}")
      (prevRound, newRound)
    }
  }

}
