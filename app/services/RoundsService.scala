package services

import controllers.RoundStat
import db.scalikejdbc.Round.RoundStatRow
import db.scalikejdbc.rewrite.ImageDbNew.SelectionQuery
import db.scalikejdbc.{Round, RoundUser, User}
import org.intracer.wmua.cmd.{DistributeImages, SetCurrentRound}

class RoundsService {

  def createNewRound(round: Round, jurorIds: Seq[Long]): Round = {
    val numberOfRounds = Round.countByContest(round.contestId)
    val created = Round.create(round.copy(number = numberOfRounds + 1))

    val prevRound = created.previous.flatMap(Round.findById)
    val jurors = User.loadJurors(round.contestId, jurorIds)

    created.addUsers(jurors.map(u =>
      RoundUser(created.getId, u.getId, u.roles.head, active = true)))

    DistributeImages.distributeImages(created, jurors, prevRound)

    SetCurrentRound(round.contestId, prevRound, created).apply()

    created
  }

  def getRoundStat(roundId: Long, round: Round): RoundStat = {
    val rounds = Round.findByContest(round.contestId)

    val statRows: Seq[RoundStatRow] = Round.roundUserStat(roundId)

    val byJuror: Map[Long, Seq[RoundStatRow]] =
      statRows.groupBy(_.juror).filter {
        case (juror, rows) => rows.map(_.count).sum > 0
      }

    val byUserCount = byJuror.mapValues(_.map(_.count).sum).toMap

    val byUserRateCount = byJuror.mapValues { v =>
      v.groupBy(_.rate)
        .mapValues {
          _.headOption.map(_.count).getOrElse(0)
        }
        .toMap
    }.toMap

    val totalByRate = Round.roundRateStat(roundId).toMap

    val total = SelectionQuery(roundId = Some(roundId), grouped = true).count()

    val roundUsers = RoundUser.byRoundId(roundId).groupBy(_.userId)
    val jurors = User
      .findByContest(round.contestId)
      .filter { u =>
        u.id.exists(byUserCount.contains)
      }
      .map(u =>
        u.copy(
          active = roundUsers.get(u.getId).flatMap(_.headOption.map(_.active))))

    RoundStat(jurors,
      round,
      rounds,
      byUserCount,
      byUserRateCount,
      total,
      totalByRate)
  }

}
