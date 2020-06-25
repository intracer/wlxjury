package org.intracer.wmua.cmd

import db.scalikejdbc.Round

case class AddRound(
                     contestId: Long,
                     number: Long = 1,
                     distribution: Int = 0,
                     rates: Int = 1,
                     name: Option[String] = None,
                     minMpx: Option[Int] = None) {

  def apply(): Round = {
    val round = Round(
      id = None,
      number,
      name.orElse(Some("Round " + number)),
      contestId,
      Set("jury"),
      distribution,
      rates = Round.ratesById(rates),
      minMpx = minMpx)

    Round.create(round)
  }

}
