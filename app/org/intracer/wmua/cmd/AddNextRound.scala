package org.intracer.wmua.cmd

import db.scalikejdbc.Round

case class AddNextRound(
                         contestId: Long,
                         roundNumber: Long,
                         distribution: Int = 0,
                         rates: Int = 1,
                         name: Option[String] = None) {

  def apply(): (Round, Round) = {
    val rounds = Round.findByContest(contestId)
    val prevRound = rounds.last
    if (prevRound.number >= roundNumber) {
      println(s"last round has number ${prevRound.number}, not going to create round number $roundNumber")

      (rounds.find(_.number == roundNumber - 1).get, rounds.find(_.number == roundNumber).get)
    } else {
      val newRound = AddRound(contestId, prevRound.number + 1, distribution, rates, name).apply()
      println(s"last round has number ${prevRound.number}")
      (prevRound, newRound)
    }
  }

}
