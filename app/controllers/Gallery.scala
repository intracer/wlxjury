package controllers

import db.scalikejdbc._
import org.intracer.wmua._
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


  def query(module: String, asUserId: Option[Long], page: Int = 1, region: String = "all", roundId: Long = 0, rate: Option[Int]) = {
    listGeneric(module, asUserId.getOrElse(0), pager => page, region, roundId, rate)
  }

  def list(asUserId: Long, page: Int = 1, region: String = "all", roundId: Long = 0, rate: Option[Int]) =
    listGeneric("gallery", asUserId, pager => page, region, roundId, rate)

  def listAtId(asUserId: Long, pageId: Long, region: String = "all", roundId: Long = 0, rate: Option[Int]) =
    listGeneric("gallery", asUserId, pager => pager.at(pageId), region, roundId, rate)

  def listGeneric(module: String, asUserId: Long, pageFn: Pager => Int, region: String = "all", roundId: Long = 0, rate: Option[Int]) = withAuth {
    user =>
      implicit request =>
        timerList.time {
          val maybeRound = if (roundId == 0) RoundJdbc.current(user) else RoundJdbc.find(roundId)

          val userContest = user.currentContest.getOrElse(0L)
          val roundContest = maybeRound.map(_.contest).getOrElse(0L)

          if (maybeRound.isEmpty ||
            (!user.hasRole("root") && userContest != roundContest) ||
            (user.roles.intersect(Set("admin", "organizer", "root")).isEmpty
              && !ContestJuryJdbc.find(userContest).exists(_.currentRound == maybeRound.flatMap(_.id))
              && !maybeRound.exists(_.juryOrgView))) {
            onUnAuthorized(user)
          } else {
            val round = maybeRound.get

            val rounds = RoundJdbc.findByContest(userContest.toLong)
            val (uFiles, asUser) = filesByUserId(asUserId, rate, user, maybeRound)

            val ratedFiles = rate.fold(
              uFiles.filter(_.selection.nonEmpty).sortBy(-_.totalRate(round))
            )(r => filterByRate(round, rate, uFiles))

            val byReg = byRegion(ratedFiles)
            val files = regionFiles(region, ratedFiles)

            val pager = new Pager(files)
            val page = pageFn(pager)
            val pageFiles = pager.pageFiles(page)

            if (round.rates.id == 1 && asUserId == 0) {
              val pageFiles2 = pageFiles.map {
                file => new ImageWithRating(
                  file.image,
                  selection = file.selection.filter(_.rate > 0),
                  countFromDb = file.countFromDb
                )
              }
              Ok(views.html.galleryByRate(user, asUserId, asUser, pageFiles2, files, page, maybeRound, rounds, region, byReg))
            } else

              module match {
                case "gallery" =>
                  Ok(views.html.gallery(user, asUserId, asUser, pageFiles, files, page, maybeRound, rounds, rate, region, byReg))

                case "filelist" =>
                  Ok(views.html.fileList(user, asUserId, asUser, files, files, page, maybeRound, rounds, rate, region, byReg, "wiki"))

                case "byrate" =>
                  if (round.rates.id != 1) {
                    Ok(views.html.galleryByRate(user, asUserId, asUser, pageFiles, files, page, maybeRound, rounds, region, byReg))
                  } else {
                    Ok(views.html.gallery(user, asUserId, asUser, pageFiles, files, page, maybeRound, rounds, None, region, byReg))
                  }
              }
          }
        }
  }

  def byRate(asUserId: Long, page: Int = 1, region: String = "all", roundId: Long = 0) =
    listGeneric("byrate", asUserId, pager => page, region, roundId, None)

  def byRateAt(asUserId: Long, pageId: Long, region: String = "all", roundId: Long = 0) =
    listGeneric("byrate", asUserId, pager => pager.at(pageId), region, roundId, None)

  def filterByRate(round: Round, rate: Option[Int], uFiles: Seq[ImageWithRating]): Seq[ImageWithRating] = {
    if (rate.isEmpty) uFiles
    else if (round.rates != Round.binaryRound) {
      if (rate.get > 0) uFiles.filter(_.rate > 0) else uFiles.filter(_.rate == 0)
    } else {
      uFiles.filter(_.rate == rate.get)
    }
  }

  def listByNumber(users: Int, page: Int = 1, region: String = "all", roundId: Long = 0, rate: Option[Int]) = withAuth {
    user =>
      implicit request =>
        val round = if (roundId == 0) RoundJdbc.current(user) else RoundJdbc.find(roundId)
        val rounds = RoundJdbc.findByContest(user.currentContest.getOrElse(0L))

        val images = round.fold(Seq.empty[ImageWithRating])(_.allImages)
        val selection = round.fold(Seq.empty[Selection])(r => SelectionJdbc.byRound(r.id.get))
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

  def fileList(asUserId: Long, page: Int = 1, region: String = "all", roundId: Long = 0, format: String = "wiki", rate: Option[Int]) = withAuth {
    user =>
      implicit request =>
        val round = if (roundId == 0) RoundJdbc.current(user) else RoundJdbc.find(roundId)
        val rounds = RoundJdbc.findByContest(user.currentContest.getOrElse(0L))
        val (uFiles, asUser) = filesByUserId(asUserId, rate, user, round)

        val ratedFiles = rate.fold(uFiles)(r => uFiles.filter(_.rate == r))
        val files = regionFiles(region, ratedFiles)

        //        val pager = new Pager(files)
        //        val pageFiles = pager.pageFiles(page)
        val byReg: Map[String, Int] = byRegion(ratedFiles)
        Ok(views.html.fileList(user, asUserId, asUser, files, files, page, round, rounds, rate, region, byReg, format))
  }

  def filesByUserId(asUserId: Long, rate: Option[Int], user: User, round: Option[Round]): (Seq[ImageWithRating], User) = {
    val maybeRoundId = round.flatMap(_.id)
    maybeRoundId.fold((Seq.empty[ImageWithRating], user)) { roundId =>
      if (asUserId == 0) {
        (rate.fold(ImageJdbc.byRoundSummedWithCriteria(roundId))(r => ImageJdbc.byRatingWithCriteriaMerged(r, roundId)), null)
      } else if (asUserId != user.id.get) {
        val asUser: User = UserJdbc.find(asUserId).get
        (userFiles(asUser, roundId), asUser)
      } else (userFiles(user, roundId), user)
    }
  }

  def listCurrent(page: Int = 1, region: String = "all", rate: Option[Int]) = withAuth {
    user =>
      implicit request =>
        Redirect(routes.Gallery.list(user.id.get, page, region, 0, rate))
  }


  def large(asUserId: Long, pageId: Long, region: String = "all", roundId: Long, rate: Option[Int], module: String) = withAuth {
    user =>
      implicit request =>
        show(pageId, user, asUserId, rate, region, roundId, module)
  }

  def largeCurrent(pageId: Long, region: String = "all", rate: Option[Int], module: String) = withAuth {
    user =>
      implicit request =>
        show(pageId, user, user.id.get, rate, region, 0, module)
  }

  def userFiles(user: User, roundId: Long): Seq[ImageWithRating] = {
    val files = //Cache.getOrElse(s"user/${user.id}/round/$roundId", 900) TODO use cache
    {
      ImageJdbc.byUserImageWithCriteriaRating(user, roundId)
    }
    user.files.clear()
    user.files ++= files

    files
  }

  def selectByPageId(roundId: Long, pageId: Long, select: Int, region: String = "all",
                     rate: Option[Int], module: String, criteria: Option[Int]): EssentialAction = withAuth {
    user =>
      implicit request =>

        val rounds = RoundJdbc.activeRounds(user.currentContest.getOrElse(0L))

        val roundOption = rounds.find(_.id.exists(_ == roundId)).filter(_.active)

        roundOption.fold(Redirect(routes.Gallery.list(user.id.get, 1, region, roundId, rate))) { round =>

          val files = filterFiles(rate, region, user, round)

          val index = files.indexWhere(_.pageId == pageId)

          val maybeFile = files.find(_.pageId == pageId)

          maybeFile.foreach { file =>
            if (criteria.isEmpty) {
              file.rate = select
              SelectionJdbc.rate(pageId = file.pageId, juryId = user.id.get, round = round.id.get, rate = select)
            } else {

              val selection = SelectionJdbc.findBy(pageId, user.id.get, roundId).get

              CriteriaRate.updateRate(selection.id, criteria.get, select)

              //            val rates = CriteriaRate.getRates(selection.id)
              //            val average =
              ///            Selection.rateWithCriteria(pageId = file.pageId, juryId = user.id.toInt, round = round.id, rate = select, criteriaId = criteria.get)
            }
          }

          checkLargeIndex(user, rate, index, pageId, files, region, round.id.get, module)
        }

    //show(index, username, rate)
  }


  def filterFiles(rate: Option[Int], region: String, user: User, round: Round): Seq[ImageWithRating] = {
    regionFiles(region, filterByRate(round, rate, userFiles(user, round.id.get)))
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

  def checkLargeIndex(
                       asUser: User,
                       rate: Option[Int],
                       index: Int,
                       pageId: Long,
                       files: Seq[ImageWithRating],
                       region: String,
                       roundId: Long,
                       module: String): Result = {
    val newIndex = if (roundId >= 124 || roundId <= 128) index
    else if (index >= files.size - 1)
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

  def show(pageId: Long, user: User, asUserId: Long, rate: Option[Int], region: String, roundId: Long, module: String)(implicit request: Request[Any]): Result = {
    timerShow.time {
      val maybeRound = if (roundId == 0) RoundJdbc.current(user) else RoundJdbc.find(roundId)

      val round = maybeRound.get

      val (allFiles, asUser) = filesByUserId(asUserId, rate, user, maybeRound)

      val sorted = if (module == "byrate") allFiles.sortBy(-_.totalRate(round)) else allFiles

      val files = regionFiles(region, filterByRate(round, rate, sorted))

      var index = files.indexWhere(_.pageId == pageId)

      val newPageId = if (index < 0) {
        files.headOption.fold(-1L)(_.pageId)
      }
      else pageId

      if (newPageId >= 0) {
        if (newPageId != pageId) {
          return Redirect(routes.Gallery.large(asUserId, newPageId, region, round.id.get, rate, module))
        }
      } else {
        return Redirect(routes.Gallery.list(asUserId, 1, region, round.id.get, rate))
      }

      val selection = if (user.canViewOrgInfo(round)) {
        SelectionJdbc.byRoundAndImageWithJury(round.id.get, pageId)
      } else Seq.empty

      index = files.indexWhere(_.pageId == newPageId)
      val page = index / (Pager.filesPerPage(files) + 1) + 1

      val byCriteria = if (roundId >= 124 && roundId <= 128) {
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

}

