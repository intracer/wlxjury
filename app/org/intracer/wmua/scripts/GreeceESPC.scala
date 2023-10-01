package org.intracer.wmua.scripts

import db.scalikejdbc.Round
import org.intracer.wmua.Tools
import org.intracer.wmua.cmd._
import play.api.Configuration
import services.RoundsService

object GreeceESPC {

  def user(role: String)(country: String, n: Int) = s"${country}ESPC$role$n"

  def main(args: Array[String]) {
    val contestId = 78L

    val cmds = Seq(
      ConnectDb("", null),
      AddUsers(contestId, "organizer", 1, user("Org")),
      AddUsers(contestId, "jury", 7, user("Juror"))
    )

    cmds.foreach(_.apply())

    val round = AddRound(contestId, 1, 0, 1).apply()

    DistributeImages.distributeImages(round, round.availableJurors, None)

    new RoundsService(Round).setCurrentRound(None, round)
  }
}
