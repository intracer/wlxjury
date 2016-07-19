package controllers

import db.scalikejdbc.{ContestJuryJdbc, RoundJdbc, SelectionJdbc}
import org.intracer.wmua._
import org.intracer.wmua.cmd.SetCurrentRound
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.Controller
import play.api.Play.current
import play.api.i18n.Messages.Implicits._

import scala.util.Try

object Rounds extends Controller with Secured {

  def rounds(contestIdParam: Option[Long] = None) = withAuth({
    user =>
      implicit request =>
        val roundsView = for (contestId <- user.currentContest.orElse(contestIdParam);
                              contest <- ContestJuryJdbc.byId(contestId)) yield {
          val rounds = RoundJdbc.findByContest(contestId)
          val currentRound = rounds.find(_.id == contest.currentRound)

          Ok(views.html.rounds(user, rounds, editRoundForm,
            imagesForm.fill(contest.images),
            selectRoundForm.fill(contest.currentRound.map(_.toString)),
            currentRound, contest)
          )
        }
        roundsView.getOrElse(Redirect(routes.Login.index())) // TODO message
  }, User.ADMIN_ROLES)

  def editRound(roundId: Option[Long], contestId: Long) = withAuth({
    user =>
      implicit request =>
        val number = RoundJdbc.findByContest(contestId).size + 1
        val round = roundId.flatMap(RoundJdbc.find).getOrElse(
          new Round(id = None, contest = contestId, number = number)
        )
        val editRound = EditRound(round, None)

        val filledRound = editRoundForm.fill(editRound)

        val rounds = RoundJdbc.findByContest(contestId)

        Ok(views.html.editRound(user, filledRound, round, rounds, Some(round.contest)))
  }, User.ADMIN_ROLES)

  def saveRound() = withAuth({
    user =>
      implicit request =>

        editRoundForm.bindFromRequest.fold(
          formWithErrors => {
            // binding failure, you retrieve the form containing errors,
            val contestId: Option[Long] = formWithErrors.data.get("contest").map(_.toLong)
            val rounds = contestId.map(RoundJdbc.findByContest).getOrElse(Seq.empty)
            BadRequest(views.html.editRound(user, formWithErrors, RoundJdbc.current(user).get, rounds, contestId))
          },
          editForm => {
            val round = editForm.round
            if (round.id.isEmpty) {
              createNewRound(user, round)
            } else {
              round.id.foreach(roundId => RoundJdbc.updateRound(roundId, round))
            }
            Redirect(routes.Rounds.rounds(Some(round.contest)))
          }
        )
  }, User.ADMIN_ROLES)

  def createNewRound(user: User, round: Round): Round = {

    //  val contest: Contest = Contest.byId(round.contest).head

    val count = RoundJdbc.countByContest(round.contest)

    val created = RoundJdbc.create(round.copy(number = count + 1))

    val prevRound = created.previous.flatMap(RoundJdbc.find)

    Tools.distributeImages(created, created.jurors, prevRound,
      selectedAtLeast = created.prevSelectedBy,
      selectMinAvgRating = created.prevMinAvgRate,
      sourceCategory = created.category,
      includeCategory = created.categoryClause.map(_ > 0)
    )
    SetCurrentRound(round.contest, None, created).apply()

    created
  }

  def setRound() = withAuth({
    user =>
      implicit request =>
        val newRoundId = selectRoundForm.bindFromRequest.get

        newRoundId.map(_.toLong).foreach { id =>
          val round = RoundJdbc.find(id)
          round.foreach { r =>
            SetCurrentRound(r.contest, None, r).apply()
          }
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
        RoundJdbc.current(user).map {
          round =>
            Redirect(routes.Rounds.roundStat(round.id.get))
        }.getOrElse {
          Redirect(routes.Login.error("You don't have permission to access this page"))
        }
  }, Set(User.ADMIN_ROLE, "jury", "root") ++ User.ORG_COM_ROLES)

  def roundStat(roundId: Long) = withAuth({
    user =>
      implicit request =>

        RoundJdbc.find(roundId).map {
          round =>

            if (!user.canViewOrgInfo(round)) {
              onUnAuthorized(user)
            } else {
              val rounds = RoundJdbc.findByContest(round.contest)

              val selection = SelectionJdbc.byRoundWithCriteria(round.id.get)

              val byUserCount = selection.groupBy(_.juryId).mapValues(_.size)
              val byUserRateCount = selection.groupBy(_.juryId).mapValues(_.groupBy(_.rate).mapValues(_.size))

              val totalCount = selection.map(_.pageId).toSet.size
              val totalByRateCount = selection.groupBy(_.rate).mapValues(_.map(_.pageId).toSet.size)

              val byJurorNum = selection.filter(_.rate > 0).groupBy(_.pageId).mapValues(_.size).toSeq.map(_.swap).groupBy(_._1).mapValues(_.size)

              Ok(views.html.roundStat(user, round, rounds, byUserCount, byUserRateCount, totalCount, totalByRateCount, byJurorNum))
            }
        }.getOrElse {
          Redirect(routes.Login.error("You don't have permission to access this page"))
        }
  }, Set(User.ADMIN_ROLE, "jury", "root") ++ User.ORG_COM_ROLES)

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
      "id" -> optional(longNumber),
      "number" -> number,
      "name" -> optional(text),
      "contest" -> longNumber,
      "roles" -> text,
      "distribution" -> number,
      "rates" -> number,
      "limitMin" -> optional(number),
      "limitMax" -> optional(number),
      "recommended" -> optional(number),
      "returnTo" -> optional(text),
      "minMpx" -> text,
      "previousRound" -> optional(longNumber),
      "minJurors" -> optional(number),
      "minAvgRate" -> optional(number),
      "categoryClause" -> text,
      "source" -> optional(text)
    )(applyEdit)(unapplyEdit)
  )

  def applyEdit(id: Option[Long], num: Int, name: Option[String], contest: Long, roles: String, distribution: Int,
                rates: Int, limitMin: Option[Int], limitMax: Option[Int], recommended: Option[Int], returnTo: Option[String],
                minMpx: String,
                previousRound: Option[Long],
                prevSelectedBy: Option[Int],
                prevMinAvgRate: Option[Int],
                categoryClause: String,
                category: Option[String]
               ): EditRound = {
    val round = new Round(id, num, name, contest, Set(roles), distribution, Round.ratesById(rates),
      limitMin, limitMax, recommended,
      minMpx = Try(minMpx.toInt).toOption,
      previous = previousRound,
      prevSelectedBy = prevSelectedBy,
      prevMinAvgRate = prevMinAvgRate,
      categoryClause = Try(categoryClause.toInt).toOption,
      category = category
    )
    EditRound(round, returnTo)
  }

  def unapplyEdit(editRound: EditRound): Option[(Option[Long], Int, Option[String], Long, String, Int, Int, Option[Int],
    Option[Int], Option[Int], Option[String], String, Option[Long], Option[Int], Option[Int], String, Option[String])] = {
    val round = editRound.round
    Some(
      (round.id, round.number, round.name, round.contest, round.roles.head, round.distribution, round.rates.id,
        round.limitMin, round.limitMax, round.recommended, editRound.returnTo,
        round.minMpx.fold("No")(_.toString),
        round.previous,
        round.prevSelectedBy,
        round.prevMinAvgRate,
        round.categoryClause.fold("No")(_.toString),
        round.category)
    )
  }
}

case class EditRound(round: Round, returnTo: Option[String])
