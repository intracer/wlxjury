package org.intracer.wmua.cmd

import db.scalikejdbc.RoundJdbc
import org.intracer.wmua.Round

case class AddRound(
                     contestId: Long,
                     number: Int = 1,
                     distribution: Int = 0,
                     rates: Int = 1,
                     name: Option[String] = None) {

  def apply(): Round = {
    val round = Round(
      id = None,
      number,
      name.orElse(Some("Round " + number)),
      contestId,
      Set("jury"),
      distribution,
      rates = Round.ratesById(rates))

    RoundJdbc.create(round)
  }

}
