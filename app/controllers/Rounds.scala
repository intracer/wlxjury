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
        val roundsView = for (contestId <- user.currentContest.orElse(contestIdParam);
                              contest <- ContestJuryJdbc.byId(contestId)) yield {
          val rounds = RoundJdbc.findByContest(contestId)
          val images = contest.images

          Ok(views.html.rounds(user, rounds, editRoundForm,
            imagesForm.fill(images),
            selectRoundForm.fill(contest.currentRound.map(_.toString)),
            RoundJdbc.current(user), contest)
          )
        }
        roundsView.getOrElse(Redirect(routes.Login.index())) // TODO message
  }, User.ADMIN_ROLES)

  def editRound(id: String) = withAuth({
    user =>
      implicit request =>
        val round = RoundJdbc.find(id.toLong).get
        val editRound = EditRound(round, None)

        val filledRound = editRoundForm.fill(editRound)

        Ok(views.html.editRound(user, filledRound, RoundJdbc.current(user), Some(round.contest)))
  }, User.ADMIN_ROLES)

  def saveRound() = withAuth({
    user =>
      implicit request =>

        editRoundForm.bindFromRequest.fold(
          formWithErrors => // binding failure, you retrieve the form containing errors,
              BadRequest(views.html.editRound(user, formWithErrors, RoundJdbc.current(user))),
          editForm => {
            val round = editForm.round
            if (round.id.contains(0)) {
              createNewRound(user, round)
            } else {
              RoundJdbc.updateRound(round.id.get, round)
            }
            Redirect(routes.Rounds.rounds(Some(round.contest)))
          }
        )
  }, User.ADMIN_ROLES)

  def createNewRound(user: User, round: Round): Round = {

    //  val contest: Contest = Contest.byId(round.contest).head

    val count = RoundJdbc.countByContest(round.contest)

    val created = RoundJdbc.create(count + 1, round.name, round.contest, round.roles.head, round.distribution, round.rates.id,
      round.limitMin, round.limitMax, round.recommended)

    Tools.distributeImages(created, created.jurors, None)

    created
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
    Tools.distributeImages(round, round.jurors, None)
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
      "recommended" -> optional(number),
      "returnTo" -> optional(text)
    )(applyEdit)(unapplyEdit)
  )

  def applyEdit(id: Long, num: Int, name: Option[String], contest: Long, roles: String, distribution: Int,
                rates: Int, limitMin: Option[Int], limitMax: Option[Int], recommended: Option[Int], returnTo: Option[String]): EditRound = {
    val round = new Round(Some(id), num, name, contest, Set(roles), distribution, Round.ratesById(rates), limitMin, limitMax, recommended)
    EditRound(round, returnTo)
  }

  def unapplyEdit(editRound: EditRound): Option[(Long, Int, Option[String], Long, String, Int, Int, Option[Int], Option[Int], Option[Int], Option[String])] = {
    val round = editRound.round
    Some(
      (round.id.get, round.number, round.name, round.contest, round.roles.head, round.distribution, round.rates.id,
        round.limitMin, round.limitMax, round.recommended, editRound.returnTo))
  }
}

case class EditRound(round: Round, returnTo: Option[String])
