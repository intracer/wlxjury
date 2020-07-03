package controllers

import db.scalikejdbc.Round.RoundStatRow
import db.scalikejdbc._
import db.scalikejdbc.rewrite.ImageDbNew.SelectionQuery
import javax.inject.Inject
import org.intracer.wmua._
import org.intracer.wmua.cmd.{DistributeImages, SetCurrentRound}
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.i18n.Messages.Implicits._
import play.api.mvc.Controller
import play.api.Logger

import scala.util.Try

/**
  * Controller for displaying pages related to contest rounds
  * @param contestsController
  */
class Rounds @Inject()(val contestsController: Contests) extends Controller with Secured {

  /**
    * Shows list of rounds in a contest
    * @param contestIdParam
    * @return
    */
  def rounds(contestIdParam: Option[Long] = None) = withAuth(contestPermission(User.ADMIN_ROLES, contestIdParam)) {
    user =>
      implicit request =>
        val roundsView = for (contestId <- contestIdParam.orElse(user.currentContestId);
                              contest <- ContestJury.findById(contestId)) yield {
          val rounds = Round.findByContest(contestId)
          val currentRound = rounds.find(_.id == contest.currentRound)

          val roundsStat = ImageJdbc.roundsStat(contestId).groupBy(_._1).map {
            case (rId, s) =>
              val rateMap = s.map {
                case (_, rate, count) => rate -> count
              }.toMap
              rId -> new RateDistribution(rateMap)
          }

          Ok(views.html.rounds(user, rounds, roundsStat,
            editRoundForm, imagesForm.fill(contest.images),
            selectRoundForm,
            currentRound, contest)
          )
        }
        roundsView.getOrElse(Redirect(routes.Login.index())) // TODO message
  }

  /**
    * Shows round editing page
    * @param roundId
    * @param contestId
    * @param topImages
    * @return
    */
  def editRound(roundId: Option[Long], contestId: Long, topImages: Option[Int]) = withAuth(contestPermission(User.ADMIN_ROLES, Some(contestId))) {
    user =>
      implicit request =>
        val number = Round.findByContest(contestId).size + 1

        val round: Round = roundId.flatMap(Round.findById).getOrElse(
          new Round(id = None, contestId = contestId, number = number)
        )

        val withTopImages = topImages.map(n => round.copy(topImages = Some(n))).getOrElse(round)

        val rounds = Round.findByContest(contestId)

        val regions = contestsController.regions(contestId)

        val jurors = round.id.fold(User.loadJurors(contestId))(User.findByRoundSelection).sorted

        val editRound = EditRound(withTopImages, jurors.flatMap(_.id), None)

        val filledRound = editRoundForm.fill(editRound)

        val stat = round.id.map(id => getRoundStat(id, round))

        val prevRound = round.previous.flatMap(Round.findById)

        val images = round.id.fold(Seq.empty[Image]) { _ =>
          Try(DistributeImages.getFilteredImages(round, jurors, prevRound))
            .fold(ta => {
              Logger.logger.error("Error loading images", ta)
              Nil
            }, x => x)
        }

        Ok(views.html.editRound(user, filledRound, round.id.isEmpty, rounds, Some(round.contestId), jurors,
          jurorsMapping, regions, stat, images))
  }

  def saveRound() = withAuth(rolePermission(User.ADMIN_ROLES)) {
    user =>
      implicit request =>

        editRoundForm.bindFromRequest.fold(
          formWithErrors => {
            // binding failure, you retrieve the form containing errors,
            val contestId: Option[Long] = formWithErrors.data.get("contest").map(_.toLong)
            val rounds = contestId.map(Round.findByContest).getOrElse(Seq.empty)
            val jurors = User.loadJurors(contestId.get)
            val hasRoundId = formWithErrors.data.get("id").exists(_.nonEmpty)

            BadRequest(views.html.editRound(user, formWithErrors, newRound = !hasRoundId, rounds, contestId, jurors, jurorsMapping))
          },
          editForm => {
            val round = editForm.round.copy(active = true)
            if (round.id.isEmpty) {
              createNewRound(round, editForm.jurors)
            } else {
              round.id.foreach { roundId =>
                Round.updateRound(roundId, round)
                if (editForm.newImages) {
                  val prevRound = round.previous.flatMap(Round.findById)

                  val jurors = User.findByRoundSelection(roundId)
                  DistributeImages.distributeImages(round, jurors, prevRound)
                }
              }
            }
            Redirect(routes.Rounds.rounds(Some(round.contestId)))
          }
        )
  }

  def createNewRound(round: Round, jurorIds: Seq[Long]): Round = {

    val count = Round.countByContest(round.contestId)

    val created = Round.create(round.copy(number = count + 1))

    val prevRound = created.previous.flatMap(Round.findById)

    val jurors = User.loadJurors(round.contestId, jurorIds)

    created.addUsers(jurors.map(u => RoundUser(created.getId, u.getId, u.roles.head, true)))

    DistributeImages.distributeImages(created, jurors, prevRound)

    SetCurrentRound(round.contestId, prevRound, created).apply()

    created
  }

  def setRound() = withAuth(rolePermission(User.ADMIN_ROLES)) { user =>
    implicit request =>
      val selectRound = selectRoundForm.bindFromRequest.get

      val id = selectRound.roundId.toLong
      val round = Round.findById(id)
      round.foreach { r =>
        SetCurrentRound(r.contestId, None, r.copy(active = selectRound.active)).apply()
      }

      Redirect(routes.Rounds.rounds(round.map(_.contestId)))
  }

  def setRoundUser() = withAuth(rolePermission(User.ADMIN_ROLES)) { user =>
    implicit request =>
      val setRoundUser = setRoundUserForm.bindFromRequest.get
      RoundUser.setActive(setRoundUser.roundId.toLong, setRoundUser.userId.toLong, setRoundUser.active)
      Redirect(routes.Rounds.roundStat(setRoundUser.roundId.toLong))
  }

  def startRound() = withAuth(rolePermission(User.ADMIN_ROLES)) { user =>
      implicit request =>

        for (contestId <- user.currentContestId;
             contest <- ContestJury.findById(contestId)) {
          val rounds = Round.findByContest(contestId)

          for (currentRound <- rounds.find(_.id == contest.currentRound);
               nextRound <- rounds.find(_.number == currentRound.number + 1)) {
            ContestJury.setCurrentRound(contestId, nextRound.id)
          }
        }

        Redirect(routes.Rounds.rounds())
  }

  def distributeImages(contest: ContestJury, round: Round) {
    DistributeImages.distributeImages(round, round.availableJurors, None)
  }

  def setImages() = withAuth(rolePermission(User.ADMIN_ROLES)) { user =>
      implicit request =>
        val imagesSource: Option[String] = imagesForm.bindFromRequest.get
        for (contest <- user.currentContestId.flatMap(ContestJury.findById)) {
          ContestJury.setImagesSource(contest.getId, imagesSource)

          //val images: Seq[Page] = Await.result(Global.commons.categoryMembers(PageQuery.byTitle(imagesSource.get)), 1.minute)

          //          for (contestId <- contest.id;
          //               currentRoundId <- ContestJuryJdbc.currentRound(contestId);
          //               round <- RoundJdbc.find(currentRoundId)) {
          //            Tools.distributeImages(round, round.jurors, None)
          //          }
        }

        Redirect(routes.Rounds.rounds())

  }

  def currentRoundStat(contestId: Option[Long] = None) = withAuth(rolePermission(Set(User.ADMIN_ROLE, "jury", "root") ++ User.ORG_COM_ROLES)) {
    user =>
      implicit request =>
        val activeRound = Round.activeRounds(user).headOption.orElse {
          val currentContestId = contestId.orElse(user.currentContestId)
          currentContestId.flatMap(contestId => Round.activeRounds(contestId).filter(r => user.canViewOrgInfo(r)).lastOption)
        }

        activeRound.map { round =>
          Redirect(routes.Rounds.roundStat(round.getId))
        }.getOrElse {
          Redirect(routes.Login.error("There is no active rounds in your contest"))
        }
  }

  def roundStat(roundId: Long) = withAuth(rolePermission(Set(User.ADMIN_ROLE, "jury", "root") ++ User.ORG_COM_ROLES)) {
    user =>
      implicit request =>

        Round.findById(roundId).map { round =>

            if (!user.canViewOrgInfo(round)) {
              onUnAuthorized(user)
            } else {
              val stat = getRoundStat(roundId, round)

              Ok(views.html.roundStat(user, round, stat))
            }
        }.getOrElse {
          Redirect(routes.Login.error("Round not found"))
        }
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

  def getRoundStat(roundId: Long, round: Round): RoundStat = {
    val rounds = Round.findByContest(round.contestId)

    val statRows: Seq[RoundStatRow] = Round.roundUserStat(roundId)

    val byJuror: Map[Long, Seq[RoundStatRow]] = statRows.groupBy(_.juror).filter {
      case (juror, rows) => rows.map(_.count).sum > 0
    }

    val byUserCount = byJuror.mapValues(_.map(_.count).sum)

    val byUserRateCount = byJuror.mapValues { v =>
      v.groupBy(_.rate).mapValues {
        _.headOption.map(_.count).getOrElse(0)
      }
    }

    val totalByRate = Round.roundRateStat(roundId).toMap

    val total = SelectionQuery(roundId = Some(roundId), grouped = true).count()

    val roundUsers = RoundUser.byRoundId(roundId).groupBy(_.userId)
    val jurors = User.findByContest(round.contestId).filter { u =>
      u.id.exists(byUserCount.contains)
    }.map(u => u.copy(active = roundUsers.get(u.getId).flatMap(_.headOption.map(_.active))))

    RoundStat(jurors, round, rounds, byUserCount, byUserRateCount, total, totalByRate)
  }

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

  def nonEmptySeq[T]: Constraint[Seq[T]] = Constraint[Seq[T]]("constraint.required") { o =>
    if (o.nonEmpty) Valid else Invalid(ValidationError("error.required"))
  }

  private val jurorsMappingKV = "jurors" -> seq(text).verifying(nonEmptySeq)
  val jurorsMapping = single(jurorsMappingKV)

  val editRoundForm = Form(
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

  def applyEdit(id: Option[Long], num: Long, name: Option[String], contest: Long, roles: String, distribution: Int,
                rates: Int, returnTo: Option[String],
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
    val round = new Round(id, num, name, contest, Set(roles), distribution, Round.ratesById(rates),
      limitMin = None, limitMax = None, recommended = None,
      minMpx = Try(minMpx.toInt).toOption,
      previous = previousRound,
      prevSelectedBy = prevSelectedBy.flatMap(s => Try(s.toInt).toOption),
      prevMinAvgRate = prevMinAvgRate.flatMap(s => Try(s.toInt).toOption),
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

  def unapplyEdit(editRound: EditRound): Option[(Option[Long], Long, Option[String], Long, String, Int, Int,
    Option[String], String, Option[Long], Option[String], Option[String], Option[String], Option[String], Option[String],
    Seq[String], String, Seq[String], Boolean, Option[String], Option[Int], Option[String])] = {
    val round = editRound.round.withFixedCategories
    Some((
      round.id, round.number, round.name, round.contestId, round.roles.head, round.distribution, round.rates.id,
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
    ))
  }
}

case class SelectRound(roundId: String, active: Boolean)

case class SetRoundUser(roundId: String, userId: String, active: Boolean)

case class RoundStat(jurors: Seq[User],
                     round: Round,
                     rounds: Seq[Round],
                     byUserCount: Map[Long, Int],
                     byUserRateCount: Map[Long, Map[Int, Int]],
                     total: Int,
                     totalByRate: Map[Int, Int])

case class EditRound(round: Round, jurors: Seq[Long], returnTo: Option[String], newImages: Boolean = false)