package org.intracer.wmua.cmd

import db.scalikejdbc.{RoundJdbc, ContestJuryJdbc}
import org.intracer.wmua.Round

case class SetCurrentRound(contestId: Long, prevRound: Option[Round], round: Round) {

  def apply(): Unit = {
    println(s"Setting current round ${prevRound.fold("")(r => s"from ${r.getId}")} to ${round.getId}")

    prevRound.foreach(r => RoundJdbc.setActive(r.getId, active = false))

    RoundJdbc.setActive(round.getId, active = round.active)
  }

}
