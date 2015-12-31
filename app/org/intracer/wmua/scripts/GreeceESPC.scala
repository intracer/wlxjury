package org.intracer.wmua.scripts

import org.intracer.wmua.Tools
import org.intracer.wmua.cmd._

object GreeceESPC {

  def user(role: String)(country: String, n: Int) = s"${country}ESPC$role$n"

  def main(args: Array[String]) {
    val contestId = 78L

    val cmds = Seq(
      ConnectDb(),
      AddUsers(contestId, "organizer", 1, user("Org")),
      AddUsers(contestId, "jury", 7, user("Juror"))
    )

    cmds.foreach(_.apply())

    val round = AddRound(contestId, 1, 0, 1).apply()

    Tools.distributeImages(round, round.jurors, None)

    SetCurrentRound(contestId, None, round).apply()
  }
}
