package controllers

import db.scalikejdbc.{SelectionJdbc, ContestJuryJdbc, RoundJdbc}
import org.intracer.wmua._
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.Controller
import play.api.Play.current
import play.api.i18n.Messages.Implicits._

object Rounds extends Controller with Secured {

  def rounds(contestIdParam: Option[Long] = None) = withAuth({
    user =>
      implicit request =>
        val contestId: Option[Long] = user.currentContest.orElse(contestIdParam)
        val rounds = contestId.fold(Seq.empty[Round])(RoundJdbc.findByContest)
        val contest = contestId.flatMap(ContestJuryJdbc.byId)
        val images = contest.flatMap(_.images)

        Ok(views.html.rounds(user, rounds, editRoundForm,
          imagesForm.fill(images),
          selectRoundForm.fill(contest.map(_.currentRound.toString)),
          RoundJdbc.current(user)))
  }, User.ADMIN_ROLES)

  def editRound(id: String) = withAuth({
    user =>
      implicit request =>
        val round = RoundJdbc.find(id.toLong).get

        val filledRound = editRoundForm.fill(round)

        Ok(views.html.editRound(user, filledRound, RoundJdbc.current(user)))
  }, User.ADMIN_ROLES)

  def saveRound() = withAuth({
    user =>
      implicit request =>

        editRoundForm.bindFromRequest.fold(
          formWithErrors => // binding failure, you retrieve the form containing errors,
            BadRequest(views.html.editRound(user, formWithErrors, RoundJdbc.current(user))),
          round => {
            if (round.id.contains(0)) {
              createNewRound(user, round)
            } else {
              RoundJdbc.updateRound(round.id.get, round)
            }
            Redirect(routes.Rounds.rounds())
          }
        )
  }, User.ADMIN_ROLES)

  def createNewRound(user: User, round: Round): Round = {

    //  val contest: Contest = Contest.byId(round.contest).head

    val count = RoundJdbc.countByContest(round.contest)

    RoundJdbc.create(count + 1, round.name, round.contest, round.roles.head, round.distribution, round.rates.id,
      round.limitMin, round.limitMax, round.recommended)

  }

  def setRound() = withAuth({
    user =>
      implicit request =>
        val newRound = selectRoundForm.bindFromRequest.get

        user.currentContest.foreach { c =>
          ContestJuryJdbc.setCurrentRound(c, newRound.map(_.toLong))
        }

        Redirect(routes.Rounds.rounds())
  }, User.ADMIN_ROLES)

  def startRound() = withAuth({
    user =>
      implicit request =>

        for (contestId <- user.currentContest;
             contest <- ContestJuryJdbc.byId(contestId)) {
          val rounds = RoundJdbc.findByContest(contestId)

          for (currentRound <- rounds.find(_.id == contest.currentRound);
               nextRound <- rounds.find(_.number == currentRound.number + 1)) {
            ContestJuryJdbc.setCurrentRound(contestId, nextRound.id)
          }
        }

        Redirect(routes.Rounds.rounds())
  }, User.ADMIN_ROLES)


  def distributeImages(contest: ContestJury, round: Round) {
    ImageDistributor.distributeImages(contest.id.get, round)
  }

  def setImages() = withAuth({
    user =>
      implicit request =>
        val imagesSource: Option[String] = imagesForm.bindFromRequest.get
        for (contest <- user.currentContest.flatMap(ContestJuryJdbc.byId)) {
          ContestJuryJdbc.updateImages(contest.id.get, imagesSource)

          //val images: Seq[Page] = Await.result(Global.commons.categoryMembers(PageQuery.byTitle(imagesSource.get)), 1.minute)

          for (contestId <- contest.id;
               currentRoundId <- ContestJuryJdbc.currentRound(contestId);
               round <- RoundJdbc.find(currentRoundId)) {
            distributeImages(contest, round)
          }
        }

        Redirect(routes.Rounds.rounds())

  }, User.ADMIN_ROLES)


  def currentRoundStat() = withAuth({
    user =>
      implicit request =>
        //        val rounds = Round.findByContest(user.contest)
        val round: Round = RoundJdbc.current(user).get

        if (user.roles.contains("jury") && !round.juryOrgView) {
          onUnAuthorized(user)
        } else {

          val rounds = RoundJdbc.findByContest(user.currentContest.getOrElse(0L))

          val selection = SelectionJdbc.byRoundWithCriteria(round.id.get)

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

  def roundStat(roundId: Long) = withAuth({
    user =>
      implicit request =>
        //        val rounds = Round.findByContest(user.contest)
        val round: Round = RoundJdbc.find(roundId).get

        if (user.roles.contains("jury") && !round.juryOrgView) {
          onUnAuthorized(user)
        } else {
          val rounds = RoundJdbc.findByContest(user.currentContest.getOrElse(0L))

          val selection = SelectionJdbc.byRoundWithCriteria(round.id.get)

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

  val imagesForm = Form("images" -> optional(text))

  val selectRoundForm = Form("currentRound" -> optional(text))

  val editRoundForm = Form(
    mapping(
      "id" -> longNumber(),
      "number" -> number(),
      "name" -> optional(text()),
      "contest" -> longNumber(),
      "roles" -> text(),
      "distribution" -> number,
      "rates" -> number,
      "limitMin" -> optional(number),
      "limitMax" -> optional(number),
      "recommended" -> optional(number)
    )(Round.applyEdit)(Round.unapplyEdit)
  )
}
