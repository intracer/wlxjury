package org.intracer.wmua.cmd

import db.scalikejdbc.RoundJdbc
import org.intracer.wmua.Round

case class AddNextRound(contestId: Long, roundNumber: Int, distribution: Int = 0, rates: Int = 1, name: Option[String] = None) {

  def apply(): (Round, Round) = {
    val rounds = RoundJdbc.findByContest(contestId)
    val prevRound = rounds.last
    if (prevRound.number >= roundNumber) {
      println(s"last round has number ${prevRound.number}, not going to create round number $roundNumber")

      (rounds.find(_.number == roundNumber - 1).get, rounds.find(_.number == roundNumber).get)
    } else {
      val newRound = AddRound(contestId, prevRound.number + 1, distribution, rates).apply()
      println(s"last round has number ${prevRound.number}")
      (prevRound, newRound)
    }
  }

}
