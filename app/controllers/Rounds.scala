package controllers

import org.intracer.wmua._
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.Controller
import play.api.Play.current
import play.api.i18n.Messages.Implicits._

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

  def editRound(id: String) = withAuth({
    user =>
      implicit request =>
        val round = Round.find(id.toLong).get

        val filledRound = editRoundForm.fill(round)

        Ok(views.html.editRound(user, filledRound, Round.current(user)))
  }, Set(User.ADMIN_ROLE))

  def saveRound() = withAuth({
    user =>
      implicit request =>

        editRoundForm.bindFromRequest.fold(
          formWithErrors => // binding failure, you retrieve the form containing errors,
            BadRequest(views.html.editRound(user, formWithErrors, Round.current(user))),
          round => {
            if (round.id == 0) {
              createNewRound(user, round)
            } else {
              Round.updateRound(round.id, round)
            }
            Redirect(routes.Rounds.rounds())
          }
        )
  }, Set(User.ADMIN_ROLE))

  def createNewRound(user: User, round: Round): Round = {

    //  val contest: Contest = Contest.byId(round.contest).head

    val count = Round.countByContest(round.contest)

    Round.create(count + 1, round.name, round.contest, round.roles.head, round.distribution, round.rates.id, round.limitMin, round.limitMax, round.recommended)

  }

  def setRound() = withAuth({
    user =>
      implicit request =>
        val newRound = selectRoundForm.bindFromRequest.get

        ContestJury.setCurrentRound(user.contest, newRound.toInt)

        Redirect(routes.Rounds.rounds())
  }, Set(User.ADMIN_ROLE))

  def startRound() = withAuth({
    user =>
      implicit request =>

        val rounds = Round.findByContest(user.contest)
        val contest = ContestJury.byId(user.contest).get
        val currentRound = rounds.find(_.id.toInt == contest.currentRound).get
        val nextRound = rounds.find(_.number == currentRound.number + 1).get

        ContestJury.setCurrentRound(user.contest, nextRound.id.toInt)

        Redirect(routes.Rounds.rounds())
  }, Set(User.ADMIN_ROLE))


  def distributeImages(contest: ContestJury, round: Round) {
    ImageDistributor.distributeImages(contest, round)
  }


  def setImages() = withAuth({
    user =>
      implicit request =>
        val imagesSource: Option[String] = imagesForm.bindFromRequest.get
        val contest = ContestJury.byId(user.contest).get
        ContestJury.updateImages(contest.id, imagesSource)

        //val images: Seq[Page] = Await.result(Global.commons.categoryMembers(PageQuery.byTitle(imagesSource.get)), 1.minute)

        val round: Round = Round.find(ContestJury.currentRound(contest.id.toInt).toLong).get
        distributeImages(contest, round)

        //        Round.up

        Redirect(routes.Rounds.rounds())

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

          val selection = Selection.byRound(round.id)

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

          val selection = Selection.byRound(round.id)

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

  val imagesForm = Form("images" -> optional(text()))

  val selectRoundForm = Form("currentRound" -> text())

  val editRoundForm = Form(
    mapping(
      "id" -> longNumber(),
      "number" -> number(),
      "name" -> optional(text()),
      "contest" -> number,
      "roles" -> text(),
      "distribution" -> number,
      "rates" -> number,
      "limitMin" -> optional(number),
      "limitMax" -> optional(number),
      "recommended" -> optional(number)
    )(Round.applyEdit)(Round.unapplyEdit)
  )



}
