package org.intracer.wmua.cmd

import db.scalikejdbc.{RoundJdbc, ContestJuryJdbc}
import org.intracer.wmua.Round

case class SetCurrentRound(contestId: Long, prevRound: Round, round: Round) {

  def apply() = {
    println(s"Setting current round from ${prevRound.id.get} to ${round.id.get}")

    ContestJuryJdbc.setCurrentRound(contestId, round.id.get)
    RoundJdbc.setActive(prevRound.id.get, active = false)
    RoundJdbc.setActive(round.id.get, active = true)
  }

}
