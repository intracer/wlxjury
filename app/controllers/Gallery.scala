package controllers

import play.api.mvc.{Request, Controller}
import org.intracer.wmua._
import play.api.data.Form
import play.api.data.Forms._
import scala.collection.mutable
import play.api.mvc.SimpleResult


object Gallery extends Controller with Secured {

  //def pages = 10

  val Selected = "selected"

  val Filter = "filter"

  val UrlInProgress = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8e/Icon_tools.svg/120px-Icon_tools.svg.png"

  def list(asUserId: Int, rate: Int, page: Int = 1, region: String = "all") = withAuth {
    user =>
      implicit request =>
        val uFiles = filesByUserId(asUserId, rate, user)

        val ratedFiles = uFiles.filter(_.rate == rate)
        val files = regionFiles(region, ratedFiles)

        val pager = new Pager(files)
        val pageFiles = pager.pageFiles(page)
        val byReg: Map[String, Int] = byRegion(ratedFiles)
        Ok(views.html.gallery(user, asUserId, pageFiles, files, uFiles, page, Round.current(user), rate, region, byReg))
  }

  def filesByUserId(asUserId: Int, rate: Int, user: User): Seq[ImageWithRating] = {
    if (asUserId == 0) {
      val raw = Image.byRating(Round.current(user).id, rate)
      val merged = raw.groupBy(_.pageId).mapValues(iws => new ImageWithRating(iws.head.image, iws.map(_.selection.head)))
      merged.values.toSeq
    } else
    if (asUserId != user.id.toInt) userFiles(User.find(asUserId).get) else userFiles(user)
  }

  def listCurrent(rate: Int, page: Int = 1, region: String = "all") = withAuth {
    user =>
      implicit request =>
        Redirect(routes.Gallery.list(user.id.toInt, rate, page, region))
  }


  def large(asUserId: Int, rate: Int, index: Int, region: String = "all") = withAuth {
    user =>
      implicit request =>
        show(index, user, asUserId, rate, region)
  }

  def largeCurrent(rate: Int, index: Int, region: String = "all") = withAuth {
    user =>
      implicit request =>
        show(index, user, user.id.toInt, rate, region)
  }

  def userFiles(user: User): Seq[ImageWithRating] = {
    if (user.files.isEmpty) {
      val round = Round.current(user)
      user.files ++= Image.byUserImageWithRating(user, round.id)
    }
    user.files
  }

  def select(rate: Int, index: Int, select: Int, region: String = "all") = withAuth {
    user =>
      implicit request =>

        val round = Contest.currentRound(user.contest)

        val files = filterFiles(rate, region, user)

        val file = files(index)

        file.rate = select

        Selection.rate(pageId = file.pageId, juryId = user.id.toInt, round = round, rate = select)

        checkLargeIndex(user, rate, index, files, region)

        //show(index, username, rate)
  }

  def filterFiles(rate: Int, region: String, user: User): Seq[ImageWithRating] = {
    regionFiles(region, userFiles(user).filter(_.rate == rate))
  }

  def regionFiles(region: String, files: Seq[ImageWithRating]): Seq[ImageWithRating] = {
    region match {
      case "all" => files
      case id => files.filter(_.image.monumentId.exists(_.startsWith(id)))
    }
  }

  def byRegion(files: Seq[ImageWithRating]) = {
    files.groupBy(_.image.monumentId.getOrElse("").split("-")(0)).map{
      case (id, images) => (id, images.size)
    } + ("all" -> files.size)
  }

  def checkLargeIndex(asUser: User, rate: Int, index: Int, files: Seq[ImageWithRating], region: String): SimpleResult = {
    val newIndex = if (index > files.size - 2)
      files.size - 2
    else index

    if (newIndex >= 0) {
      Redirect(routes.Gallery.large(asUser.id.toInt, rate, newIndex, region))
    } else {
      Redirect(routes.Gallery.list(asUser.id.toInt, rate, 1, region))
    }
  }

  def show(index: Int, user: User, asUserId: Int, rate: Int, region: String)(implicit request: Request[Any]): SimpleResult = {
    val allFiles = filesByUserId(asUserId, rate, user)

    val files = regionFiles(region, allFiles.filter(_.rate == rate))

    val newIndex = if (index > files.size - 2)
      files.size - 2
    else index

    if (newIndex != index) {
      if (newIndex >= 0) {
        Redirect(routes.Gallery.large(asUserId, rate, newIndex, region))
      } else {
        Redirect(routes.Gallery.list(asUserId, rate, 1, region))
      }
    }

    val page = (index / Pager.filesPerPage(files)) + 1

    show2(index, files, user, asUserId, rate, page, 1, region)
  }


  def show2(index: Int, files: Seq[ImageWithRating], user: User, asUserId: Int, rate: Int,
            page: Int, round: Int = 1, region: String)
           (implicit request: Request[Any]): SimpleResult = {
    val extraRight = if (index - 2 < 0) 2 - index else 0
    val extraLeft = if (files.size < index + 3) index + 3 - files.size else 0

    val left = Math.max(0, index - 2)
    val right = Math.min(index + 3, files.size)
    val start = Math.max(0, left - extraLeft)
    var end = Math.min(files.size, right + extraRight)
    val monument = files(index).image.monumentId.flatMap(MonumentJdbc.find)

    Ok(views.html.large(user, asUserId, files, index, start, end, page, rate, region, monument))
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