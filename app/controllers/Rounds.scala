package controllers

import db.scalikejdbc.RoundJdbc.RoundStatRow
import db.scalikejdbc._
import db.scalikejdbc.rewrite.ImageDbNew.SelectionQuery
import org.intracer.wmua._
import org.intracer.wmua.cmd.SetCurrentRound
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.i18n.Messages.Implicits._
import play.api.mvc.Controller
import scalikejdbc._

import scala.util.Try

object Rounds extends Controller with Secured {

  def rounds(contestIdParam: Option[Long] = None) = withAuth(contestPermission(User.ADMIN_ROLES, contestIdParam)) {
    user =>
      implicit request =>
        val roundsView = for (contestId <- user.currentContest.orElse(contestIdParam);
                              contest <- ContestJuryJdbc.byId(contestId)) yield {
          val rounds = RoundJdbc.findByContest(contestId)
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
            selectRoundForm.fill(contest.currentRound.map(_.toString)),
            currentRound, contest)
          )
        }
        roundsView.getOrElse(Redirect(routes.Login.index())) // TODO message
  }

  def editRound(roundId: Option[Long], contestId: Long) = withAuth(rolePermission(User.ADMIN_ROLES)) {
    user =>
      implicit request =>
        val number = RoundJdbc.findByContest(contestId).size + 1
        val round: Round = roundId.flatMap(RoundJdbc.find).getOrElse(
          new Round(id = None, contest = contestId, number = number)
        )

        val rounds = RoundJdbc.findByContest(contestId)

        val regions = Contests.regions(contestId)

        val jurors = round.id.fold(loadJurors(contestId))(UserJdbc.findByRoundSelection)

        val editRound = EditRound(round, jurors.flatMap(_.id), None)

        val filledRound = editRoundForm.fill(editRound)

        val stat = round.id.map(id => getRoundStat(id, round))

        val prevRound = round.previous.flatMap(RoundJdbc.find)

        val images = round.id.fold(Seq.empty[Image])(id => Tools.getFilteredImages(round, jurors, prevRound))

        Ok(views.html.editRound(user, filledRound, round.id.isEmpty, rounds, Some(round.contest), jurors, regions, stat, images))
  }

  def loadJurors(contestId: Long): Seq[User] = {
    UserJdbc.findAllBy(sqls.in(UserJdbc.u.roles, Seq("jury")).and.eq(UserJdbc.u.contest, contestId))
  }

  def loadJurors(contestId: Long, jurorIds: Seq[Long]): Seq[User] = {
    UserJdbc.findAllBy(
      sqls
        .in(UserJdbc.u.id, jurorIds).and
        .in(UserJdbc.u.roles, Seq("jury")).and
        .eq(UserJdbc.u.contest, contestId)
    )
  }

  def saveRound() = withAuth(rolePermission(User.ADMIN_ROLES)) {
    user =>
      implicit request =>

        editRoundForm.bindFromRequest.fold(
          formWithErrors => {
            // binding failure, you retrieve the form containing errors,
            val contestId: Option[Long] = formWithErrors.data.get("contest").map(_.toLong)
            val rounds = contestId.map(RoundJdbc.findByContest).getOrElse(Seq.empty)
            val jurors = loadJurors(contestId.get)
            val hasRoundId = formWithErrors.data.get("id").exists(_.nonEmpty)

            BadRequest(views.html.editRound(user, formWithErrors, newRound = !hasRoundId, rounds, contestId, jurors))
          },
          editForm => {
            val round = editForm.round.copy(active = true)
            if (round.id.isEmpty) {
              createNewRound(user, round, editForm.jurors)
            } else {
              round.id.foreach{ roundId =>
                RoundJdbc.updateRound(roundId, round)
                if (editForm.newImages) {
                  val prevRound = round.previous.flatMap(RoundJdbc.find)

                  val jurors = UserJdbc.findByRoundSelection(roundId)
                  Tools.distributeImages(round, jurors, prevRound)
                }
              }
            }
            Redirect(routes.Rounds.rounds(Some(round.contest)))
          }
        )
  }

  def createNewRound(user: User, round: Round, jurorIds: Seq[Long]): Round = {

    //  val contest: Contest = Contest.byId(round.contest).head

    val count = RoundJdbc.countByContest(round.contest)

    val created = RoundJdbc.create(round.copy(number = count + 1))

    val prevRound = created.previous.flatMap(RoundJdbc.find)

    val jurors = loadJurors(round.contest, jurorIds)

    Tools.distributeImages(created, jurors, prevRound)

    SetCurrentRound(round.contest, None, created).apply()

    created
  }

  def setRound() = withAuth(rolePermission(User.ADMIN_ROLES)) {
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
  }

  def startRound() = withAuth(rolePermission(User.ADMIN_ROLES)) {
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
  }

  def distributeImages(contest: ContestJury, round: Round) {
    Tools.distributeImages(round, round.jurors, None)
  }

  def setImages() = withAuth(rolePermission(User.ADMIN_ROLES)) {
    user =>
      implicit request =>
        val imagesSource: Option[String] = imagesForm.bindFromRequest.get
        for (contest <- user.currentContest.flatMap(ContestJuryJdbc.byId)) {
          ContestJuryJdbc.updateImages(contest.id.get, imagesSource)

          //val images: Seq[Page] = Await.result(Global.commons.categoryMembers(PageQuery.byTitle(imagesSource.get)), 1.minute)

//          for (contestId <- contest.id;
//               currentRoundId <- ContestJuryJdbc.currentRound(contestId);
//               round <- RoundJdbc.find(currentRoundId)) {
//            Tools.distributeImages(round, round.jurors, None)
//          }
        }

        Redirect(routes.Rounds.rounds())

  }

  def currentRoundStat() = withAuth(rolePermission(Set(User.ADMIN_ROLE, "jury", "root") ++ User.ORG_COM_ROLES)){
    user =>
      implicit request =>
        RoundJdbc.current(user).map {
          round =>
            Redirect(routes.Rounds.roundStat(round.id.get))
        }.getOrElse {
          Redirect(routes.Login.error("There is no active rounds in your contest"))
        }
  }

  def roundStat(roundId: Long) = withAuth(rolePermission(Set(User.ADMIN_ROLE, "jury", "root") ++ User.ORG_COM_ROLES)) {
    user =>
      implicit request =>

        RoundJdbc.find(roundId).map {
          round =>

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
    val rounds = RoundJdbc.findByContest(round.contest)

    val statRows: Seq[RoundStatRow] = RoundJdbc.roundUserStat(roundId)

    val byJuror: Map[Long, Seq[RoundStatRow]] = statRows.groupBy(_.juror).filter{
      case (juror, rows) => rows.map(_.count).sum > 0
    }

    val byUserCount = byJuror.mapValues(_.map(_.count).sum)

    val byUserRateCount = byJuror.mapValues { v =>
      v.groupBy(_.rate).mapValues {
        _.headOption.map(_.count).getOrElse(0)
      }
    }

    val totalByRate = RoundJdbc.roundRateStat(roundId).toMap

    val total = SelectionQuery(roundId = Some(roundId), grouped = true).count()

    val jurors = UserJdbc.findByContest(round.contest).filter { u =>
      u.id.exists(byUserCount.contains)
    }

    val stat = RoundStat(jurors, round, rounds, byUserCount, byUserRateCount, total, totalByRate)
    stat
  }

  val imagesForm = Form("images" -> optional(text))

  val selectRoundForm = Form("currentRound" -> optional(text))

  def nonEmptySeq[T]: Constraint[Seq[T]] = Constraint[Seq[T]]("constraint.required") { o =>
    if (o.nonEmpty) Valid else Invalid(ValidationError("error.required"))
  }

  private val jurorsMappingKV = "jurors" -> seq(text).verifying(nonEmptySeq)
  val jurorsMapping = single(jurorsMappingKV)

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
      "source" -> optional(text),
      "regions" -> seq(text),
      "minSize" -> text,
      jurorsMappingKV,
      "newImages" -> boolean,
      "monumentIds" -> optional(text)
    )(applyEdit)(unapplyEdit)
  )

  def applyEdit(id: Option[Long], num: Int, name: Option[String], contest: Long, roles: String, distribution: Int,
                rates: Int, limitMin: Option[Int], limitMax: Option[Int], recommended: Option[Int], returnTo: Option[String],
                minMpx: String,
                previousRound: Option[Long],
                prevSelectedBy: Option[Int],
                prevMinAvgRate: Option[Int],
                categoryClause: String,
                category: Option[String],
                regions: Seq[String],
                minImageSize: String,
                jurors: Seq[String],
                newImages: Boolean,
                monumentIds: Option[String]
               ): EditRound = {
    val round = new Round(id, num, name, contest, Set(roles), distribution, Round.ratesById(rates),
      limitMin, limitMax, recommended,
      minMpx = Try(minMpx.toInt).toOption,
      previous = previousRound,
      prevSelectedBy = prevSelectedBy,
      prevMinAvgRate = prevMinAvgRate,
      categoryClause = Try(categoryClause.toInt).toOption,
      category = category,
      regions = if (regions.nonEmpty) Some(regions.mkString(",")) else None,
      minImageSize = Try(minImageSize.toInt).toOption,
      monuments = monumentIds
    )
    EditRound(round, jurors.map(_.toLong), returnTo, newImages)
  }

  def unapplyEdit(editRound: EditRound): Option[(Option[Long], Int, Option[String], Long, String, Int, Int, Option[Int],
    Option[Int], Option[Int], Option[String], String, Option[Long], Option[Int], Option[Int], String, Option[String],
    Seq[String], String, Seq[String], Boolean, Option[String])] = {
    val round = editRound.round
    Some((
      round.id, round.number, round.name, round.contest, round.roles.head, round.distribution, round.rates.id,
      round.limitMin, round.limitMax, round.recommended, editRound.returnTo,
      round.minMpx.fold("No")(_.toString),
      round.previous,
      round.prevSelectedBy,
      round.prevMinAvgRate,
      round.categoryClause.fold("No")(_.toString),
      round.category,
      round.regionIds,
      round.minImageSize.fold("No")(_.toString),
      editRound.jurors.map(_.toString),
      editRound.newImages,
      round.monuments
      ))
  }
}

case class RoundStat(jurors: Seq[User],
                     round: Round,
                     rounds: Seq[Round],
                     byUserCount: Map[Long, Int],
                     byUserRateCount: Map[Long, Map[Int, Int]],
                     total: Int,
                     totalByRate: Map[Int, Int])

case class EditRound(round: Round, jurors: Seq[Long], returnTo: Option[String], newImages: Boolean = false)