package controllers

import controllers.Admin._
import org.intracer.wmua._
import play.api.mvc.Controller

object Rounds extends Controller with Secured {

  def rounds() = withAuth({
    user =>
      implicit request =>
        val rounds = Round.findByContest(user.contest)
        val contest = ContestJury.byId(user.contest).get

        Ok(views.html.rounds(user, rounds, editRoundForm,
          imagesForm.fill(Some(contest.getImages)),
          selectRoundForm.fill(contest.currentRound.toString),
          Round.current(user)))
  }, Set(User.ADMIN_ROLE))

  def currentRoundStat() = withAuth({
    user =>
      implicit request =>
        //        val rounds = Round.findByContest(user.contest)
        val round: Round = Round.current(user)

        if (user.roles.contains("jury") && !round.juryOrgView) {
          onUnAuthorized(user)
        } else {

          val rounds = Round.findByContest(user.contest)

          val selection = Selection.byRoundWithCriteria(round.id)

          val byUserCount = selection.groupBy(_.juryId).mapValues(_.size)
          val byUserRateCount = selection.groupBy(_.juryId).mapValues(_.groupBy(_.rate).mapValues(_.size))

          val totalCount = selection.map(_.pageId).toSet.size
          val totalByRateCount = selection.groupBy(_.rate).mapValues(_.map(_.pageId).toSet.size)

          val byPageId: Map[Long, Seq[Selection]] = selection.filter(_.rate > 0).groupBy(_.pageId)
          val pageIdToNumber: Map[Long, Int] = byPageId.mapValues(_.size)
          val swapped: Seq[(Int, Long)] = pageIdToNumber.toSeq.map(_.swap)
          val grouped: Map[Int, Seq[(Int, Long)]] = swapped.groupBy(_._1)
          val byJurorNum = grouped.mapValues(_.size)

          Ok(views.html.roundStat(user, round, rounds, byUserCount, byUserRateCount, totalCount, totalByRateCount, byJurorNum))
        }
  }, Set(User.ADMIN_ROLE, "jury") ++ User.ORG_COM_ROLES)

  def roundStat(roundId: Int) = withAuth({
    user =>
      implicit request =>
        //        val rounds = Round.findByContest(user.contest)
        val round: Round = Round.find(roundId.toLong).get

        if (user.roles.contains("jury") && !round.juryOrgView) {
          onUnAuthorized(user)
        } else {
          val rounds = Round.findByContest(user.contest)

          val selection = Selection.byRoundWithCriteria(round.id)

          val byUserCount = selection.groupBy(_.juryId).mapValues(_.size)
          val byUserRateCount = selection.groupBy(_.juryId).mapValues(_.groupBy(_.rate).mapValues(_.size))

          val totalCount = selection.map(_.pageId).toSet.size
          val totalByRateCount = selection.groupBy(_.rate).mapValues(_.map(_.pageId).toSet.size)

          val byJurorNum = selection.filter(_.rate > 0).groupBy(_.pageId).mapValues(_.size).toSeq.map(_.swap).groupBy(_._1).mapValues(_.size)

          Ok(views.html.roundStat(user, round, rounds, byUserCount, byUserRateCount, totalCount, totalByRateCount, byJurorNum))
        }
  }, Set(User.ADMIN_ROLE, "jury") ++ User.ORG_COM_ROLES)

//  def byRate(roundId: Int) = withAuth({
//    user =>
//      implicit request =>
//        val round: Round = Round.find(roundId.toLong).get
//        val rounds = Round.findByContest(user.contest)
//
//        val images = Image.byRoundMerged(round.id.toInt)
//
////        val byUserCount = selection.groupBy(_.juryId).mapValues(_.size)
////        val byUserRateCount = selection.groupBy(_.juryId).mapValues(_.groupBy(_.rate).mapValues(_.size))
////
////        val totalCount = selection.map(_.pageId).toSet.size
////        val totalByRateCount = selection.groupBy(_.rate).mapValues(_.map(_.pageId).toSet.size)
//
//        val imagesByRate = images.sortBy(-_.totalRate)
//
////        Ok(views.html.galleryByRate(user, round, imagesByRate))
//  })


}
