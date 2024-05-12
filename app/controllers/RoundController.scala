package controllers

import db.scalikejdbc._
import org.intracer.wmua._
import org.intracer.wmua.cmd.DistributeImages
import play.api.Logging
import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.i18n.I18nSupport
import play.api.mvc.{ControllerComponents, EssentialAction}
import services.RoundService

import javax.inject.Inject
import scala.util.Try

/** Controller for displaying pages related to contest rounds
  * @param contestsController
  */
class RoundController @Inject() (
    cc: ControllerComponents,
    val contestsController: ContestController,
    roundsService: RoundService
) extends Secured(cc)
    with I18nSupport
    with Logging {

  /** Shows list of rounds in a contest
    * @param contestIdParam
    * @return
    */
  def rounds(contestIdParam: Option[Long] = None): EssentialAction =
    withAuth(contestPermission(User.ADMIN_ROLES, contestIdParam)) { user => implicit request =>
      val roundsView =
        for (
          contestId <- contestIdParam.orElse(user.currentContest);
          contest <- ContestJuryJdbc.findById(contestId)
        ) yield {
          val rounds = Round.findByContest(contestId)
          Ok(
            views.html.rounds(
              user,
              rounds,
              ImageJdbc.roundsStat(contestId, rounds.size).toMap,
              editRoundForm,
              imagesForm.fill(contest.images),
              selectRoundForm,
              rounds.find(_.id == contest.currentRound),
              contest
            )
          )
        }
      roundsView.getOrElse(Redirect(routes.LoginController.index)) // TODO message
    }

  /** Shows round editing page
    * @param roundId
    * @param contestId
    * @param topImages
    * @return
    */
  def editRound(roundId: Option[Long], contestId: Long, topImages: Option[Int]): EssentialAction =
    withAuth(contestPermission(User.ADMIN_ROLES, Some(contestId))) { user => implicit request =>
      val rounds = Round.findByContest(contestId)

      val round: Round = roundId
        .flatMap(Round.findById)
        .getOrElse(
          new Round(id = None, contestId = contestId, number = rounds.size + 1)
        )

      val withTopImages = topImages.map(n => round.copy(topImages = Some(n))).getOrElse(round)

      val regions = contestsController.regions(contestId)
      val jurors = round.id.fold(User.loadJurors(contestId))(User.findByRoundSelection).sorted
      val editRound = EditRound(withTopImages, jurors.flatMap(_.id), None)
      val filledRound = editRoundForm.fill(editRound)
      val stat = round.id.map(id => roundsService.getRoundStat(id, round))
      val prevRound = round.previous.flatMap(Round.findById)
      val images = round.id.map(_ => DistributeImages.getFilteredImages(round, jurors, prevRound)).getOrElse(Nil)
      Ok(
        views.html.editRound(
          user,
          filledRound,
          round.id.isEmpty,
          rounds,
          Some(round.contestId),
          jurors,
          jurorsMapping,
          regions,
          stat,
          images
        )
      )
    }

  def saveRound(): EssentialAction =
    withAuth(rolePermission(User.ADMIN_ROLES)) { user => implicit request =>
      editRoundForm
        .bindFromRequest()
        .fold(
          formWithErrors => {
            val contestId: Option[Long] = formWithErrors.data.get("contest").map(_.toLong)
            BadRequest(
              views.html.editRound(
                user,
                formWithErrors,
                newRound = !formWithErrors.data.get("id").exists(_.nonEmpty),
                rounds = contestId.map(Round.findByContest).getOrElse(Nil),
                contestId = contestId,
                jurors = User.loadJurors(contestId.get),
                jurorsMapping = jurorsMapping
              )
            )
          },
          editForm => {
            val round = editForm.round.copy(active = true)

            if (round.id.isEmpty) {
              roundsService.createNewRound(round, editForm.jurors)
            } else {
              for {
                roundId <- round.id
                currentRound <- Round.findById(roundId)
              } yield {
                Round.updateRound(roundId, round)
                if (editForm.newImages) {
                  val prevRound = round.previous.flatMap(Round.findById)
                  val jurors = User.findByRoundSelection(roundId)
                  DistributeImages.distributeImages(currentRound, jurors, prevRound)
                }
              }
            }
            Redirect(routes.RoundController.rounds(Some(round.contestId)))
          }
        )
    }

  def setRound(): EssentialAction = withAuth(rolePermission(User.ADMIN_ROLES)) { user => implicit request =>
    val selectRound = selectRoundForm.bindFromRequest().get

    val id = selectRound.roundId.toLong
    val round = Round.findById(id)
    round.foreach { r =>
      roundsService.setCurrentRound(None, r.copy(active = selectRound.active))
    }

    Redirect(routes.RoundController.rounds(round.map(_.contestId)))
  }

  def setRoundUser(): EssentialAction =
    withAuth(rolePermission(User.ADMIN_ROLES)) { user => implicit request =>
      val setRoundUser = setRoundUserForm.bindFromRequest().get
      RoundUser.setActive(setRoundUser.roundId.toLong, setRoundUser.userId.toLong, setRoundUser.active)
      Redirect(routes.RoundController.roundStat(setRoundUser.roundId.toLong))
    }

  def setImages(): EssentialAction =
    withAuth(rolePermission(User.ADMIN_ROLES)) { user => implicit request =>
      val imagesSource: Option[String] = imagesForm.bindFromRequest().get
      for (contest <- user.currentContest.flatMap(ContestJuryJdbc.findById)) {
        ContestJuryJdbc.setImagesSource(contest.getId, imagesSource)

        // val images: Seq[Page] = Await.result(Global.commons.categoryMembers(PageQuery.byTitle(imagesSource.get)), 1.minute)

        //          for (contestId <- contest.id;
        //               currentRoundId <- ContestJuryJdbc.currentRound(contestId);
        //               round <- RoundJdbc.find(currentRoundId)) {
        //            Tools.distributeImages(round, round.jurors, None)
        //          }
      }

      Redirect(routes.RoundController.rounds())

    }

  def currentRoundStat(contestId: Option[Long] = None): EssentialAction =
    withAuth(rolePermission(Set(User.ADMIN_ROLE, "jury", "root") ++ User.ORG_COM_ROLES)) { user => implicit request =>
      val currentContestId = contestId.orElse(user.currentContest)
      val activeRound = Round
        .activeRounds(user)
        .headOption
        .orElse {
          currentContestId.flatMap { contestId =>
            Round
              .activeRounds(contestId)
              .filter(r => user.canViewOrgInfo(r))
              .lastOption
          }
        }
        .orElse {
          currentContestId.flatMap { contestId =>
            Round
              .findByContest(contestId)
              .filter(r => user.canViewOrgInfo(r))
              .lastOption
          }
        }

      activeRound
        .map { round =>
          Redirect(routes.RoundController.roundStat(round.getId))
        }
        .getOrElse {
          Redirect(routes.LoginController.error("There is no active rounds in your contest"))
        }
    }

  def roundStat(roundId: Long): EssentialAction =
    withAuth(rolePermission(Set(User.ADMIN_ROLE, "jury", "root") ++ User.ORG_COM_ROLES)) { user => implicit request =>
      Round
        .findById(roundId)
        .map { round =>
          if (!user.canViewOrgInfo(round)) {
            onUnAuthorized(user)
          } else {
            val stat = roundsService.getRoundStat(roundId, round)

            Ok(views.html.roundStat(user, round, stat))
          }
        }
        .getOrElse {
          Redirect(routes.LoginController.error("Round not found"))
        }
    }

  def mergeRounds(): EssentialAction =
    withAuth(rolePermission(User.ADMIN_ROLES)) { user => implicit request =>
      val mergeRounds = mergeRoundsForm.bindFromRequest().get
      roundsService.mergeRounds(user.contestId.get, mergeRounds.targetRoundId, mergeRounds.sourceRoundId)
      Redirect(routes.RoundController.rounds())
    }

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

  val selectRoundForm = Form(
    mapping(
      "currentId" -> text,
      "setActive" -> boolean
    )(SelectRound.apply)(SelectRound.unapply)
  )

  val setRoundUserForm = Form(
    mapping(
      "parentId" -> text,
      "currentId" -> text,
      "setActive" -> boolean
    )(SetRoundUser.apply)(SetRoundUser.unapply)
  )

  val mergeRoundsForm = Form(
    mapping(
      "targetRoundId" -> longNumber,
      "sourceRoundId" -> longNumber
    )(MergeRoundsForm.apply)(MergeRoundsForm.unapply)
  )
  case class MergeRoundsForm(targetRoundId: Long, sourceRoundId: Long)

  def nonEmptySeq[T]: Constraint[Seq[T]] =
    Constraint[Seq[T]]("constraint.required") { o =>
      if (o.nonEmpty) Valid else Invalid(ValidationError("error.required"))
    }

  private val jurorsMappingKV = "jurors" -> seq(text).verifying(nonEmptySeq)
  val jurorsMapping: Mapping[Seq[String]] = single(jurorsMappingKV)

  val editRoundForm: Form[EditRound] = Form(
    mapping(
      "id" -> optional(longNumber),
      "number" -> longNumber,
      "name" -> optional(text),
      "contest" -> longNumber,
      "roles" -> text,
      "distribution" -> number,
      "rates" -> number,
      "returnTo" -> optional(text),
      "minMpx" -> text,
      "previousRound" -> optional(longNumber),
      "minJurors" -> optional(text),
      "minAvgRate" -> optional(text),
      "categoryClause" -> optional(text),
      "source" -> optional(text),
      "excludeCategory" -> optional(text),
      "regions" -> seq(text),
      "minSize" -> text,
      jurorsMappingKV,
      "newImages" -> boolean,
      "monumentIds" -> optional(text),
      "topImages" -> optional(number),
      "specialNomination" -> optional(text)
    )(applyEdit)(unapplyEdit)
  )

  def applyEdit(
      id: Option[Long],
      num: Long,
      name: Option[String],
      contest: Long,
      roles: String,
      distribution: Int,
      rates: Int,
      returnTo: Option[String],
      minMpx: String,
      previousRound: Option[Long],
      prevSelectedBy: Option[String],
      prevMinAvgRate: Option[String],
      categoryClause: Option[String],
      category: Option[String],
      excludeCategory: Option[String],
      regions: Seq[String],
      minImageSize: String,
      jurors: Seq[String],
      newImages: Boolean,
      monumentIds: Option[String],
      topImages: Option[Int],
      specialNomination: Option[String]
  ): EditRound = {
    val round = new Round(
      id,
      num,
      name,
      contest,
      Set(roles),
      distribution,
      Round.ratesById(rates),
      limits = RoundLimits(),
      minMpx = Try(minMpx.toInt).toOption,
      previous = previousRound,
      prevSelectedBy = prevSelectedBy.flatMap(s => Try(s.toInt).toOption),
      prevMinAvgRate = prevMinAvgRate.flatMap(s => Try(BigDecimal(s)).toOption),
      categoryClause = categoryClause.map(_.toInt),
      category = category,
      excludeCategory = excludeCategory,
      regions = if (regions.nonEmpty) Some(regions.mkString(",")) else None,
      minImageSize = Try(minImageSize.toInt).toOption,
      monuments = monumentIds,
      topImages = topImages,
      specialNomination = specialNomination
    ).withFixedCategories
    EditRound(round, jurors.flatMap(s => Try(s.toLong).toOption), returnTo, newImages)
  }

  def unapplyEdit(editRound: EditRound): Option[
    (
        Option[Long],
        Long,
        Option[String],
        Long,
        String,
        Int,
        Int,
        Option[String],
        String,
        Option[Long],
        Option[String],
        Option[String],
        Option[String],
        Option[String],
        Option[String],
        Seq[String],
        String,
        Seq[String],
        Boolean,
        Option[String],
        Option[Int],
        Option[String]
    )
  ] = {
    val round = editRound.round.withFixedCategories
    Some(
      (
        round.id,
        round.number,
        round.name,
        round.contestId,
        round.roles.head,
        round.distribution,
        round.rates.id,
        editRound.returnTo,
        round.minMpx.fold("No")(_.toString),
        round.previous,
        round.prevSelectedBy.map(_.toString),
        round.prevMinAvgRate.map(_.toString),
        round.categoryClause.map(_.toString),
        round.category,
        round.excludeCategory,
        round.regionIds,
        round.minImageSize.fold("No")(_.toString),
        editRound.jurors.map(_.toString),
        editRound.newImages,
        round.monuments,
        round.topImages,
        round.specialNomination
      )
    )
  }
}

case class SelectRound(roundId: String, active: Boolean)

case class SetRoundUser(roundId: String, userId: String, active: Boolean)

case class RoundStat(
    jurors: Seq[User],
    round: Round,
    rounds: Seq[Round],
    byUserCount: Map[Long, Int],
    byUserRateCount: Map[Long, Map[Int, Int]],
    total: Int,
    totalByRate: Map[Int, Int]
)

case class EditRound(round: Round, jurors: Seq[Long], returnTo: Option[String], newImages: Boolean = false)
