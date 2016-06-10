package org.intracer.wmua.cmd

import db.scalikejdbc.{RoundJdbc, ContestJuryJdbc}
import org.intracer.wmua.Round

case class SetCurrentRound(contestId: Long, prevRound: Option[Round], round: Round) {

  def apply() = {
    println(s"Setting current round ${prevRound.fold("")(r => s"from ${r.id.get}")} to ${round.id.get}")

//    prevRound.foreach(r => RoundJdbc.setActive(r.id.get, active = false))

    RoundJdbc.setInActiveAllInContest(contestId)

    ContestJuryJdbc.setCurrentRound(contestId, round.id)
    RoundJdbc.setActive(round.id.get, active = true)
  }

}
