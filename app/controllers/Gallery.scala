package controllers

import org.intracer.wmua._
import play.api.cache.Cache
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages.Implicits._
import play.api.mvc.{Controller, EssentialAction, Request, Result}

object Gallery extends Controller with Secured with Instrumented {

  import play.api.Play.current

  //def pages = 10

  val Selected = "selected"

  val Filter = "filter"

  val UrlInProgress = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8e/Icon_tools.svg/120px-Icon_tools.svg.png"

  private[this] val timerList = metrics.timer("Gallery.list")
  private[this] val timerByRate = metrics.timer("Gallery.byRate")
  private[this] val timerShow = metrics.timer("Gallery.show")


  def query(module: String, asUserId: Option[Int], page: Int = 1, region: String = "all", roundId: Int = 0, rate: Option[Int]) = {
    listGeneric(module, asUserId.getOrElse(0), pager => page, region, roundId, rate )
  }

  def list(asUserId: Int, page: Int = 1, region: String = "all", roundId: Int = 0, rate: Option[Int]) =
    listGeneric("gallery", asUserId, pager => page, region, roundId, rate )

  def listAtId(asUserId: Int, pageId: Long, region: String = "all", roundId: Int = 0, rate: Option[Int]) =
    listGeneric("gallery", asUserId, pager => pager.at(pageId), region, roundId, rate )

  def listGeneric(module: String, asUserId: Int, pageFn: Pager => Int, region: String = "all", roundId: Int = 0, rate: Option[Int]) = withAuth {
    user =>
      implicit request =>
        timerList.time {
          val round = if (roundId == 0) Round.current(user) else Round.find(roundId).get

          val userContest = user.contest
          val roundContest = round.contest

          if (userContest != roundContest ||
            (user.roles.intersect(Set("admin", "organizer")).isEmpty
              && !ContestJury.byId(userContest).exists(_.currentRound == roundId)
              && !round.juryOrgView)) {
            onUnAuthorized(user)
          } else {

            val rounds = Round.findByContest(userContest.toLong)
            val (uFiles, asUser) = filesByUserId(asUserId, rate, user, round)

            val ratedFiles = rate.fold(uFiles.sortBy(-_.totalRate(round)))(r => filterByRate(round, rate, uFiles))
            val byReg = byRegion(ratedFiles)
            val files = regionFiles(region, ratedFiles)

            val pager = new Pager(files)
            val page = pageFn(pager)
            val pageFiles = pager.pageFiles(page)

            if (round.rates.id == 1 && asUserId == 0) {
              val pageFiles2 = pageFiles.map(file => new ImageWithRating(file.image, selection = file.selection.filter(_.rate > 0), countFromDb = file.countFromDb))
              Ok(views.html.galleryByRate(user, asUserId, asUser, pageFiles2, files, page, round, rounds, region, byReg))
            } else

            module match {
              case "gallery" =>
                Ok(views.html.gallery(user, asUserId, asUser, pageFiles, files, page, round, rounds, rate, region, byReg))

              case "filelist" =>
                Ok(views.html.fileList(user, asUserId, asUser, files, files, page, round, rounds, rate, region, byReg, "wiki"))

              case "byrate" =>
                if (round.rates.id != 1) {
                  Ok(views.html.galleryByRate(user, asUserId, asUser, pageFiles, files, page, round, rounds, region, byReg))
                } else {
                  Ok(views.html.gallery(user, asUserId, asUser, pageFiles, files, page, round, rounds, None, region, byReg))
                }
            }
          }
        }
  }

  def byRateGeneric(asUserId: Int, pageFn: Pager => Int, region: String = "all", roundId: Int = 0) = withAuth {
    user =>
      implicit request =>
        timerByRate.time {

          val round = if (roundId == 0) Round.current(user) else Round.find(roundId).get
          val rounds = Round.findByContest(user.contest.toLong)
          val (uFiles, asUser) = filesByUserId(asUserId, None, user, round)

          val byReg = byRegion(uFiles)
          val files = regionFiles(region, uFiles).sortBy(-_.totalRate(round))

          val pager = new Pager(files)
          val page = pageFn(pager)
          val pageFiles = pager.pageFiles(page)
          val pageFiles2 = pageFiles.map(file => new ImageWithRating(file.image, selection = file.selection.filter(_.rate > 0), countFromDb = file.countFromDb))

          if (round.rates.id != 1) {
            Ok(views.html.galleryByRate(user, asUserId, asUser, pageFiles2, files, page, round, rounds, region, byReg))
          } else {
            Ok(views.html.gallery(user, asUserId, asUser, pageFiles, files, page, round, rounds, None, region, byReg))
          }
        }
  }

  def byRate(asUserId: Int, page: Int = 1, region: String = "all", roundId: Int = 0) =
    byRateGeneric(asUserId, pager => page, region, roundId)

  def byRateAt(asUserId: Int, pageId: Long, region: String = "all", roundId: Int = 0) =
    byRateGeneric(asUserId, pager => pager.at(pageId), region, roundId)

  def filterByRate(round: Round, rate: Option[Int], uFiles: Seq[ImageWithRating]): Seq[ImageWithRating] = {
    if (rate.isEmpty) uFiles
    else if (round.rates != Round.binaryRound) {
      if (rate.get > 0) uFiles.filter(_.rate > 0) else uFiles.filter(_.rate == 0)
    } else {
      uFiles.filter(_.rate == rate.get)
    }
  }

  def listByNumber(users: Int, page: Int = 1, region: String = "all", roundId: Int = 0, rate: Option[Int]) = withAuth {
    user =>
      implicit request =>
        val round = if (roundId == 0) Round.current(user) else Round.find(roundId).get
        val rounds = Round.findByContest(user.contest.toLong)

        val images = round.allImages
        val selection = Selection.byRound(round.id)
        val ratedSelection = rate.fold(selection)(r => selection.filter(_.rate == r))

        val byPageId = ratedSelection.groupBy(_.pageId).filter(_._2.size == users)

        val imagesWithSelection = images.flatMap {
          image =>
            if (byPageId.contains(image.pageId)) {
              Some(new ImageWithRating(image.image, byPageId(image.pageId)))
            } else {
              None
            }
        }

        val files = regionFiles(region, imagesWithSelection)

        val pager = new Pager(files)
        val pageFiles = pager.pageFiles(page)
        val byReg: Map[String, Int] = byRegion(imagesWithSelection)
        Ok(views.html.gallery(user, 0, null, pageFiles, files, page, round, rounds, rate, region, byReg))
  }

  def fileList(asUserId: Int, page: Int = 1, region: String = "all", roundId: Int = 0, format: String = "wiki", rate: Option[Int]) = withAuth {
    user =>
      implicit request =>
        val round = if (roundId == 0) Round.current(user) else Round.find(roundId).get
        val rounds = Round.findByContest(user.contest.toLong)
        val (uFiles, asUser) = filesByUserId(asUserId, rate, user, round)

        val ratedFiles = rate.fold(uFiles)(r => uFiles.filter(_.rate == r))
        val files = regionFiles(region, ratedFiles)

        //        val pager = new Pager(files)
        //        val pageFiles = pager.pageFiles(page)
        val byReg: Map[String, Int] = byRegion(ratedFiles)
        Ok(views.html.fileList(user, asUserId, asUser, files, files, page, round, rounds, rate, region, byReg, format))
  }

  def filesByUserId(asUserId: Int, rate: Option[Int], user: User, round: Round): (Seq[ImageWithRating], User) = {
    if (asUserId == 0) {
      (rate.fold(ImageJdbc.byRoundSummed(round.id))(r => ImageJdbc.byRatingMerged(r, round.id.toInt)), null)
    } else
    if (asUserId != user.id.toInt) {
      val asUser: User = User.find(asUserId).get
      (userFiles(asUser, round.id), asUser)
    } else (userFiles(user, round.id), user)
  }

  def listCurrent(page: Int = 1, region: String = "all", rate: Option[Int]) = withAuth {
    user =>
      implicit request =>
        Redirect(routes.Gallery.list(user.id.toInt, page, region, 0, rate))
  }


  def large(asUserId: Int, pageId: Long, region: String = "all", roundId: Int, rate: Option[Int], module: String) = withAuth {
    user =>
      implicit request =>
        show(pageId, user, asUserId, rate, region, roundId, module)
  }

  def largeCurrent(pageId: Long, region: String = "all", rate: Option[Int], module: String) = withAuth {
    user =>
      implicit request =>
        show(pageId, user, user.id.toInt, rate, region, 0, module)
  }

  def userFiles(user: User, roundId: Long): Seq[ImageWithRating] = {
    val files = Cache.getOrElse(s"user/${user.id}/round/$roundId", 900){
      ImageJdbc.byUserImageWithRating(user, roundId)
    }
    user.files.clear()
    user.files ++= files

    files
  }

  def selectByPageId(roundId: Int, pageId: Long, select: Int, region: String = "all", rate: Option[Int], module: String): EssentialAction  = withAuth {
    user =>
      implicit request =>

        val rounds = Round.activeRounds(user.contest)

        val roundOption = rounds.find(_.id.toInt == roundId).filter(_.active)

        roundOption.fold(Redirect(routes.Gallery.list(user.id.toInt, 1, region, roundId, rate))) { round =>

          val files = filterFiles(rate, region, user, round)

          val file = files.find(_.pageId == pageId).get

          val index = files.indexWhere(_.pageId == pageId)

          file.rate = select

          Selection.rate(pageId = file.pageId, juryId = user.id.toInt, round = round.id, rate = select)

          checkLargeIndex(user, rate, index, pageId, files, region, round.id.toInt, module)
        }

    //show(index, username, rate)
  }


  def filterFiles(rate: Option[Int], region: String, user: User, round: Round): Seq[ImageWithRating] = {
    regionFiles(region, filterByRate(round, rate, userFiles(user, round.id)))
  }

  def regionFiles(region: String, files: Seq[ImageWithRating]): Seq[ImageWithRating] = {
    region match {
      case "all" => files
      case id => files.filter(_.image.monumentId.exists(_.startsWith(id)))
    }
  }

  def byRegion(files: Seq[ImageWithRating]): Map[String, Int] = {
    files.groupBy(_.image.monumentId.getOrElse("").split("-")(0)).map {
      case (id, images) => (id, images.size)
    } + ("all" -> files.size)
  }

  def checkLargeIndex(asUser: User, rate: Option[Int], index: Int, pageId: Long, files: Seq[ImageWithRating], region: String, roundId: Int, module: String): Result = {
      val newIndex = if (index > files.size - 2)
        files.size - 2
      else index + 1

    val newPageId = if (newIndex < 0)
      files.lastOption.fold(-1L)(_.pageId)
    else files(newIndex).pageId

    if (newIndex >= 0) {
      Redirect(routes.Gallery.large(asUser.id.toInt, newPageId, region, roundId, rate, module))
    } else {

      if (module == "gallery") {
        Redirect(routes.Gallery.list(asUser.id.toInt, 1, region, roundId, rate))
      } else {
        Redirect(routes.Gallery.byRate(asUser.id.toInt, 1, region, roundId))
      }
    }
  }

  def show(pageId: Long, user: User, asUserId: Int, rate: Option[Int], region: String, roundId: Int, module: String)(implicit request: Request[Any]): Result = {
    timerShow.time {
      val round = if (roundId == 0) Round.current(user) else Round.find(roundId).get

      val (allFiles, asUser) = filesByUserId(asUserId, rate, user, round)

      val sorted = if (module == "byrate") allFiles.sortBy(-_.totalRate(round)) else allFiles

      val files = regionFiles(region, filterByRate(round, rate, sorted))

      var index = files.indexWhere(_.pageId == pageId)

      val newPageId = if (index < 0) {
        files.headOption.fold(-1L)(_.pageId)
      }
      else pageId

      if (newPageId >= 0) {
        if (newPageId != pageId) {
          return Redirect(routes.Gallery.large(asUserId, newPageId, region, round.id.toInt, rate, module))
        }
      } else {
        return Redirect(routes.Gallery.list(asUserId, 1, region, round.id.toInt, rate))
      }

      val selection = if (user.canViewOrgInfo(round)) {
        Selection.byRoundAndImageWithJury(round.id, pageId)
      } else Seq.empty

      index = files.indexWhere(_.pageId == newPageId)
      val page = index / (Pager.filesPerPage(files) + 1) + 1

      show2(index, files, user, asUserId, rate, page, round, region, module, selection)
    }
  }


  def show2(index: Int, files: Seq[ImageWithRating], user: User, asUserId: Int, rate: Option[Int],
            page: Int, round: Round, region: String, module: String, selection: Seq[(Selection, User)])
           (implicit request: Request[Any]): Result = {
    val extraRight = if (index - 2 < 0) 2 - index else 0
    val extraLeft = if (files.size < index + 3) index + 3 - files.size else 0

    val left = Math.max(0, index - 2)
    val right = Math.min(index + 3, files.size)
    val start = Math.max(0, left - extraLeft)
    val end = Math.min(files.size, right + extraRight)
    val monument = files(index).image.monumentId.flatMap(MonumentJdbc.find)

    val comments = CommentJdbc.findByRoundAndSubject(round.id.toInt, files(index).pageId)


    Ok(views.html.large.large(user, asUserId, files, index, start, end, page, rate, region, round, monument, module, comments, selection))
  }

  val loginForm = Form(
    tuple(
      "login" -> nonEmptyText(),
      "password" -> nonEmptyText()
    ) verifying("invalid.user.or.password", fields => fields match {
      case (l, p) => User.login(l, p).isDefined
    })
  )

}