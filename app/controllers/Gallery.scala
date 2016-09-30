package controllers

import db.scalikejdbc._
import db.scalikejdbc.rewrite.ImageDbNew
import db.scalikejdbc.rewrite.ImageDbNew.{Limit, SelectionQuery}
import org.intracer.wmua._
import play.api.i18n.Messages.Implicits._
import play.api.mvc.{Controller, EssentialAction, Request, Result}

/**
  * Backend for getting and displaying images
  */
object Gallery extends Controller with Secured with Instrumented {

  import Pager._
  import play.api.Play.current

  //def pages = 10

  val Selected = "selected"

  val Filter = "filter"

  val UrlInProgress = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8e/Icon_tools.svg/120px-Icon_tools.svg.png"

  private[this] val timerList = metrics.timer("Gallery.list")
  private[this] val timerByRate = metrics.timer("Gallery.byRate")
  private[this] val timerShow = metrics.timer("Gallery.show")

  def moduleByUserId(asUserId: Long) = if (asUserId == 0) "byrate" else "gallery"

  /**
    * Queries images
    *
    * @param module   one of "gallery", "byrate" or "filelist"
    * @param asUserId view images assigned to specific user
    * @param page     show specific page
    * @param region   region code to filter
    * @param roundId  round id
    * @param rate     filter by rate. For select/reject rounds it is selected: 1, rejected: -1, unrated: 0
    * @return
    */
  def query(module: String, asUserId: Option[Long], page: Int = 1, region: String = "all", roundId: Long = 0, rate: Option[Int]) =
    listGeneric(module, asUserId.getOrElse(0), pageOffset(page), region, roundId, rate)

  def list(asUserId: Long, page: Int = 1, region: String = "all", roundId: Long = 0, rate: Option[Int]) =
    listGeneric(moduleByUserId(asUserId), asUserId, pageOffset(page), region, roundId, rate)

  def listAtId(asUserId: Long, pageId: Long, region: String = "all", roundId: Long = 0, rate: Option[Int]) =
    listGeneric(moduleByUserId(asUserId), asUserId, startPageId(pageId), region, roundId, rate)

  def byRate(asUserId: Long, page: Int = 1, region: String = "all", roundId: Long = 0) =
    listGeneric("byrate", asUserId, pageOffset(page), region, roundId)

  def byRateAt(asUserId: Long, pageId: Long, region: String = "all", roundId: Long = 0) =
    listGeneric("byrate", asUserId, startPageId(pageId), region, roundId, None)

  def fileList(asUserId: Long, page: Int = 1, region: String = "all", roundId: Long = 0, format: String = "wiki", rate: Option[Int]) =
    listGeneric("filelist", asUserId, pageOffset(page), region, roundId, rate)

  def listCurrent(page: Int = 1, region: String = "all", rate: Option[Int]) = withAuth {
    user =>
      implicit request =>
        Redirect(routes.Gallery.list(user.id.get, page, region, 0, rate))
  }

  def listGeneric(
                   module: String,
                   asUserId: Long,
                   pager: Pager,
                   region: String = "all",
                   roundId: Long = 0,
                   rate: Option[Int] = None) = withAuth {
    user =>
      implicit request =>
        timerList.time {
          val maybeRound = if (roundId == 0) RoundJdbc.current(user) else RoundJdbc.find(roundId)

          val roundContest = maybeRound.map(_.contest).getOrElse(0L)

          if (isNotAuthorized(user, maybeRound, roundContest)) {
            onUnAuthorized(user)
          } else {
            val round = maybeRound.get
            val rounds = RoundJdbc.findByContest(roundContest)

            val asUser = getAsUser(asUserId, user)

            val sortedFiles = filesByUserId(asUserId, rate, round, pager, userDetails = module == "filelist")

            val filesInRegion = regionFiles(region, sortedFiles)

            val byReg = byRegion(sortedFiles)

            val rates = rateDistribution(user, round)

         //   val ranks = ImageWithRating.rankImages(sortedFiles, round)
            val useTable = !round.isBinary || asUserId == 0

            module match {
              case "gallery" =>
                Ok(views.html.gallery(user, asUserId, asUser,
                  filesInRegion, pager, maybeRound, rounds, rate, region, byReg, rates)
                )
              case "filelist" =>
                val ranks = ImageWithRating.rankImages(sortedFiles, round)
                Ok(views.html.fileList(user, asUserId, asUser,
                  filesInRegion, ranks, pager, maybeRound, rounds, rate, region, byReg, "wiki", useTable, rates)
                )
              case "byrate" =>
              //  if (region != "grouped") {
                  Ok(views.html.galleryByRate(user, asUserId, asUser, filesInRegion, pager, maybeRound, rounds, rate, region, byReg, rates))
              //    Ok(views.html.galleryByRateRegions(user, asUserId, asUser, sortedFiles, ranks, pages, page, startImage, maybeRound, rounds, region, byReg))
              //  }
            }
          }
        }
  }

  def getSortedImages(
                       asUserId: Long,
                       rate: Option[Int],
                       round: Round,
                       module: String,
                       pager: Pager = Pager.pageOffset(1)): Seq[ImageWithRating] = {
    val userDetails = module == "filelist"
    filesByUserId(asUserId, rate, round, pager, userDetails)
  }

  def isNotAuthorized(user: User, maybeRound: Option[Round], roundContest: Long): Boolean = {
    val userContest = user.currentContest.getOrElse(0L)
    val notAuthorized = maybeRound.isEmpty ||
      (!user.hasRole("root") && userContest != roundContest) ||
      (user.roles.intersect(Set("admin", "organizer", "root")).isEmpty
        && !ContestJuryJdbc.find(userContest).exists(_.currentRound == maybeRound.flatMap(_.id))
        && !maybeRound.exists(_.juryOrgView))
    notAuthorized
  }

  def getAsUser(asUserId: Long, user: User): User = {
    if (asUserId == 0) {
      null
    } else if (asUserId != user.id.get) {
      UserJdbc.find(asUserId).get
    } else {
      user
    }
  }

  def filesByUserId(
                     userId: Long,
                     rate: Option[Int],
                     round: Round,
                     pager: Pager,
                     userDetails: Boolean = false): Seq[ImageWithRating] = {
    val query = getQuery(userId, rate, round, Some(pager), userDetails)
    pager.setCount(query.count())

    val withPageIdOffset = pager.startPageId.fold(query) {
      pageId =>
        val rank = query.imageRank(pageId)
        val pageSize = pager.pageSize
        val page = rank / pageSize
        val offset = page * pageSize
        pager.page = page + 1
        query.copy(limit = Some(Limit(Some(pageSize), Some(offset))))
    }

    withPageIdOffset.list()
  }

  def getQuery(userId: Long, rate: Option[Int], round: Round, pager: Option[Pager] = None, userDetails: Boolean = false): SelectionQuery = {
    val userIdOpt = Some(userId).filter(_ != 0)
    val query = ImageDbNew.SelectionQuery(
      userId = userIdOpt,
      rate = rate,
      roundId = round.id,
      order = Map("rate" -> -1, "s.page_id" -> 1),
      grouped = userIdOpt.isEmpty && !userDetails,
      groupWithDetails = userDetails,
      limit = pager.map(p => Limit(Some(p.pageSize), p.offset, p.startPageId))
    )
    query
  }

  def large(asUserId: Long, pageId: Long, region: String = "all", roundId: Long, rate: Option[Int], module: String) = withAuth {
    user =>
      implicit request =>
        show(pageId, user, asUserId, rate, region, roundId, module)
  }

  def largeCurrentUser(pageId: Long, region: String = "all", rate: Option[Int], module: String) = withAuth {
    user =>
      implicit request =>
        show(pageId, user, user.id.get, rate, region, 0, module)
  }

  def selectByPageId(roundId: Long, pageId: Long, select: Int, region: String = "all",
                     rate: Option[Int], module: String, criteria: Option[Int]): EssentialAction = withAuth {
    user =>
      implicit request =>

        val rounds = RoundJdbc.activeRounds(user.currentContest.getOrElse(0L))

        val roundOption = rounds.find(_.id.exists(_ == roundId)).filter(_.active)

        roundOption.fold(Redirect(routes.Gallery.list(user.id.get, 1, region, roundId, rate))) { round =>

          val files = filesByUserId(user.id.get, rate, round, Pager.startPageId(pageId))

          val index = files.indexWhere(_.pageId == pageId)

          val maybeFile = files.find(_.pageId == pageId)

          maybeFile.foreach { file =>
            if (criteria.isEmpty) {
              file.rate = select
              SelectionJdbc.rate(pageId = file.pageId, juryId = user.id.get, round = round.id.get, rate = select)
            } else {

              val selection = SelectionJdbc.findBy(pageId, user.id.get, roundId).get

              CriteriaRate.updateRate(selection.id, criteria.get, select)
            }
          }

          checkLargeIndex(user, rate, index, pageId, files, region, round.id.get, module)
        }
  }

  def regionFiles(region: String, files: Seq[ImageWithRating]): Seq[ImageWithRating] = {
    region match {
      case "all" | "grouped" => files
      case id => files.filter(_.image.monumentId.exists(_.startsWith(id)))
    }
  }

  def byRegion(files: Seq[ImageWithRating]): Map[String, Int] = {
    val stat = files.groupBy(_.image.monumentId.map(_.split("-")(0))).collect {
      case (Some(id), images) => (id, images.size)
    }

    if (stat.nonEmpty)
      stat + ("all" -> files.size)
    else
      stat
  }

  def checkLargeIndex(
                       asUser: User,
                       rate: Option[Int],
                       index: Int,
                       pageId: Long,
                       files: Seq[ImageWithRating],
                       region: String,
                       roundId: Long,
                       module: String): Result = {
    val newIndex = if (index >= files.size - 1)
      files.size - 2
    else index + 1

    val newPageId = if (newIndex < 0)
      files.lastOption.fold(-1L)(_.pageId)
    else files(newIndex).pageId

    if (newIndex >= 0) {
      Redirect(routes.Gallery.large(asUser.id.get, newPageId, region, roundId, rate, module))
    } else {

      if (module == "gallery") {
        Redirect(routes.Gallery.list(asUser.id.get, 1, region, roundId, rate))
      } else {
        Redirect(routes.Gallery.byRate(asUser.id.get, 1, region, roundId))
      }
    }
  }

  def show(
            pageId: Long,
            user: User,
            asUserId: Long,
            rate: Option[Int],
            region: String,
            roundId: Long,
            module: String)(implicit request: Request[Any]): Result = {
    timerShow.time {
      val maybeRound = if (roundId == 0) RoundJdbc.current(user) else RoundJdbc.find(roundId)

      val round = maybeRound.get

      val query = getQuery(asUserId, rate, round)

      val rank = query.imageRank(pageId)

      val offset = Math.max(0, rank - 3)

      val files = query.copy(limit = Some(Limit(Some(5), Some(offset)))).list()

      val index = files.indexWhere(_.pageId == pageId)

      if (index < 0) {
        return Redirect(if (files.nonEmpty) {
          routes.Gallery.large(asUserId, files.head.pageId, region, round.id.get, rate, module)
        } else {
          routes.Gallery.list(asUserId, 1, region, round.id.get, rate)
        })
      }

      val selection = if (user.canViewOrgInfo(round)) {
        SelectionJdbc.byRoundAndImageWithJury(round.id.get, pageId)
      } else Seq.empty

      val page = index / (Pager.pageSize + 1) + 1

      val byCriteria = if (round.hasCriteriaRate) {
        val criterias = {
          val selection = SelectionJdbc.findBy(pageId, asUserId, roundId).get
          CriteriaRate.getRates(selection.id)
        }

        criterias.map { c => c.criteria.toInt -> c.rate }.toMap
      } else Map.empty[Int, Int]

      show2(index, files, user, asUserId, rate, page, round, region, module, selection, byCriteria)
    }
  }

  def show2(index: Int,
            files: Seq[ImageWithRating],
            user: User,
            asUserId: Long,
            rate: Option[Int],
            page: Int,
            round: Round,
            region: String,
            module: String,
            selection: Seq[(Selection, User)],
            byCriteria: Map[Int, Int] = Map.empty)
           (implicit request: Request[Any]): Result = {
    val extraRight = if (index - 2 < 0) 2 - index else 0
    val extraLeft = if (files.size < index + 3) index + 3 - files.size else 0

    val left = Math.max(0, index - 2)
    val right = Math.min(index + 3, files.size)
    val start = Math.max(0, left - extraLeft)
    val end = Math.min(files.size, right + extraRight)
    val monument = files(index).image.monumentId.flatMap(id => if (id.trim.nonEmpty) MonumentJdbc.find(id) else None)

    val comments = CommentJdbc.findByRoundAndSubject(round.id.get, files(index).pageId)


    Ok(
      views.html.large.large(
        user, asUserId,
        files, index, start, end, page, rate,
        region, round, monument, module, comments, selection,
        byCriteria
      )
    )
  }

  def rateDistribution(user: User, round: Round) = {
    val rateMap = ImageJdbc.rateDistribution(user.id.get, round.id.get)
    new RateDistribution(rateMap)
  }
}

class RateDistribution(rateMap: Map[Int, Int]) {

  def unrated = rateMap.getOrElse(0, 0)

  def selected = rateMap.getOrElse(1, 0)

  def rejected = rateMap.getOrElse(-1, 0)

  def all = rateMap.values.sum

  def positive = all - unrated - rejected

  def rated = all - unrated

}

