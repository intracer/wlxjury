package controllers

import akka.http.scaladsl.model.ContentTypes
import akka.stream.scaladsl.Source
import akka.util.ByteString
import db.scalikejdbc._
import db.scalikejdbc.rewrite.ImageDbNew
import db.scalikejdbc.rewrite.ImageDbNew.{Limit, SelectionQuery}
import org.intracer.wmua._
import play.api.http.HttpEntity
import play.api.i18n.Messages.Implicits._
import play.api.mvc._

/**
  * Backend for getting and displaying images
  */
object Gallery extends Controller with Secured with Instrumented {

  import Pager._
  import play.api.Play.current

  val Selected = "selected"

  val Filter = "filter"

  val UrlInProgress = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8e/Icon_tools.svg/120px-Icon_tools.svg.png"

  private[this] val timerList = metrics.timer("Gallery.list")
  private[this] val timerByRate = metrics.timer("Gallery.byRate")

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
  def query(module: String, asUserId: Option[Long], page: Int = 1, region: String = "all", roundId: Long = 0,
            rate: Option[Int], rated: Option[Boolean] = None) =
    listGeneric(module, asUserId.getOrElse(0), pageOffset(page), region, roundId, rate, rated)

  def list(asUserId: Long, page: Int = 1, region: String = "all", roundId: Long = 0, rate: Option[Int]) =
    listGeneric(moduleByUserId(asUserId), asUserId, pageOffset(page), region, roundId, rate)

  def listAtId(asUserId: Long, pageId: Long, region: String = "all", roundId: Long = 0, rate: Option[Int]) =
    listGeneric(moduleByUserId(asUserId), asUserId, startPageId(pageId), region, roundId, rate)

  def byRate(asUserId: Long, page: Int = 1, region: String = "all", roundId: Long = 0, rated: Option[Boolean] = None) =
    listGeneric("byrate", asUserId, pageOffset(page), region, roundId, rated = rated)

  def byRateAt(asUserId: Long, pageId: Long, region: String = "all", roundId: Long = 0, rated: Option[Boolean] = None) =
    listGeneric("byrate", asUserId, startPageId(pageId), region, roundId, None, rated = rated)

  def fileList(asUserId: Long, page: Int = 1, region: String = "all", roundId: Long = 0, format: String = "wiki", rate: Option[Int]) =
    listGeneric("filelist", asUserId, pageOffset(page), region, roundId, rate)

  def listCurrent(page: Int = 1, region: String = "all", rate: Option[Int]) = withAuth() {
    user =>
      implicit request =>
        Redirect(routes.Gallery.list(user.getId, page, region, 0, rate))
  }

  def listGeneric(
                   module: String,
                   asUserId: Long,
                   pager: Pager,
                   region: String = "all",
                   roundId: Long = 0,
                   rate: Option[Int] = None,
                   rated: Option[Boolean] = None) = withAuth() { user =>
    implicit request =>
        timerList.time {
          val maybeRound = if (roundId == 0) RoundJdbc.current(user).headOption else RoundJdbc.findById(roundId)

          val roundContestId = maybeRound.map(_.contestId).getOrElse(0L)
          val round = maybeRound.get
          val rounds = if (user.canViewOrgInfo(round)) {
            RoundJdbc.findByContest(roundContestId)
          } else {
            RoundJdbc.current(user)
          }

          if (isNotAuthorized(user, maybeRound, roundContestId, rounds)) {
            onUnAuthorized(user)
          } else {

            lazy val asUser = getAsUser(asUserId, user)

            val regions = Set(region).filter(_ != "all")

            val userDetails = Set("filelist", "csv").contains(module)
            val query = getQuery(asUserId, rate, round.id, Some(pager), userDetails, rated, regions)
            pager.setCount(query.count())

            val files = filesByUserId(query, pager, userDetails)

            val contest = ContestJuryJdbc.findById(roundContestId).get

            val byReg = if (contest.monumentIdTemplate.isDefined) {
              query.copy(regions = Set.empty).byRegionStat()
            } else {
              Seq.empty
            }

            val rates = rateDistribution(user, round)

            //   val ranks = ImageWithRating.rankImages(sortedFiles, round)
            val useTable = !round.isBinary || asUserId == 0

            module match {
              case "gallery" =>
                Ok(views.html.gallery(user, asUserId,
                  files, pager, maybeRound, rounds, rate, region, byReg, rates)
                )
              case "csv" =>
                val jurors = if (user.canViewOrgInfo(round) && asUserId == 0) {
                  UserJdbc.findByRoundSelection(roundId)
                } else {
                  Seq(asUser)
                }
                val data = Csv.exportRates(files, jurors, round)
                val name = "WLXJury_Round_" + round.description
                csvStream(data, name)

              case "filelist" =>
                val ranks = ImageWithRating.rankImages(files, round)

                val jurors = if (user.canViewOrgInfo(round) && asUserId == 0) {
                  UserJdbc.findByRoundSelection(roundId)
                } else {
                  Seq(asUser)
                }

                Ok(views.html.fileList(user, asUserId, asUser,
                  files, ranks, jurors, pager, maybeRound, rounds, rate, region, byReg, "wiki", useTable, rates)
                )
              case "byrate" =>
                if (region != "grouped") {
                  Ok(views.html.galleryByRate(user, asUserId, files, pager, maybeRound, rounds, rate, region, byReg, rates, rated))
                } else {
                  Ok(views.html.galleryByRateRegions(user, asUserId, files, pager, maybeRound, rounds, rate, region, byReg))
                }
            }
          }
        }
  }

  def getSortedImages(
                       asUserId: Long,
                       rate: Option[Int],
                       roundId: Option[Long],
                       module: String,
                       pager: Pager = Pager.pageOffset(1)): Seq[ImageWithRating] = {
    val userDetails = module == "filelist"
    filesByUserId(getQuery(asUserId, rate, roundId, Some(pager), userDetails), pager, userDetails)
  }

  def isNotAuthorized(user: User, maybeRound: Option[Round], roundContest: Long, rounds: Seq[Round]): Boolean = {
    val userContest = user.currentContest.getOrElse(0L)
    val notAuthorized = maybeRound.isEmpty ||
      (!user.hasRole("root") && userContest != roundContest) ||
      (user.roles.intersect(Set("admin", "organizer", "root")).isEmpty
        && !rounds.exists(_.id == maybeRound.flatMap(_.id))
        && !maybeRound.exists(_.juryOrgView))
    notAuthorized
  }

  def getAsUser(asUserId: Long, user: User): User = {
    if (asUserId == 0) {
      null
    } else if (asUserId != user.getId) {
      UserJdbc.findById(asUserId).get
    } else {
      user
    }
  }

  def filesByUserId(query: SelectionQuery,
                    pager: Pager,
                    userDetails: Boolean = false): Seq[ImageWithRating] = {

    val withPageIdOffset = pager.startPageId.fold(query) {
      pageId =>
        val rank = query.imageRank(pageId)
        val pageSize = pager.pageSize
        val page = rank / pageSize
        val offset = page * pageSize
        pager.page = page + 1
        query.copy(limit = Some(Limit(Some(pageSize), Some(offset))))
    }.copy(limit = query.limit.filter(_ => !userDetails))

    withPageIdOffset.list()
  }

  def getQuery(
                userId: Long,
                rate: Option[Int],
                roundId: Option[Long],
                pager: Option[Pager] = None,
                userDetails: Boolean = false,
                rated: Option[Boolean] = None,
                regions: Set[String] = Set.empty): SelectionQuery = {
    val userIdOpt = Some(userId).filter(_ != 0)

    ImageDbNew.SelectionQuery(
      userId = userIdOpt,
      rate = rate,
      rated = rated,
      roundId = roundId,
      regions = regions,
      order = Map("rate" -> -1, "s.page_id" -> 1),
      grouped = userIdOpt.isEmpty && !userDetails,
      groupWithDetails = userDetails,
      limit = pager.map(p => Limit(Some(p.pageSize), p.offset, p.startPageId))
    )
  }

  def selectWS(roundId: Long, pageId: Long, select: Int, region: String = "all",
               rate: Option[Int], module: String, criteria: Option[Int]): EssentialAction = withAuth() {
    user =>
      implicit request =>

        SelectionJdbc.rate(pageId = pageId, juryId = user.getId, roundId = roundId, rate = select)

        Ok("success")
  }

  def rateDistribution(user: User, round: Round) = {
    val rateMap = ImageJdbc.rateDistribution(user.getId, round.getId)
    new RateDistribution(rateMap)
  }

  def csvStream(lines: Seq[Seq[String]], filename: String): Result = {
    val withBom = Csv.addBom(lines).toList
    val source = Source[Seq[String]](withBom).map { line =>
      ByteString(Csv.writeRow(line))
    }
    Result(
      ResponseHeader(OK, Map("Content-Disposition" -> ("attachment; filename=\"" + filename + ".csv\""))),
      HttpEntity.Streamed(source, None, Some(ContentTypes.`text/csv(UTF-8)`.value))
    )
  }

  def resizedImagesUrls(contestId: Long) = {
    val images = ImageJdbc.findByContestId(contestId)
    val urls = for (image <- images;
                    (x, y) <- Global.smallSizes;
                    factor <- Seq(1.0, 1.5, 2.0)
    ) yield Global.resizeTo(image, (x * factor).toInt, (y * factor).toInt)

    urls.sorted.distinct
  }

  def urlsStream(urls: collection.immutable.Iterable[String], filename: String): Result = {
    val source = Source[String](urls).map { line =>
      ByteString(line + "\n")
    }
    Result(
      ResponseHeader(OK, Map("Content-Disposition" -> ("attachment; filename=\"" + filename + ".txt\""))),
      HttpEntity.Streamed(source, None, Some(ContentTypes.`text/plain(UTF-8)`.value))
    )
  }

  def thumbnailUrls(contestId: Long) = withAuth() {
    user =>
      implicit request =>
        val contest = ContestJuryJdbc.findById(contestId).get
        if (user.isAdmin(Some(contestId))) {
          val urls = resizedImagesUrls(contestId)
          val name = "WLXJury_Round_Thumb_Urls" + contest.fullName.replaceAll(" ", "_")
          urlsStream(urls, name)
        }
        else {
          onUnAuthorized(user)
        }
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