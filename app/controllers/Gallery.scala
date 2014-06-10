package controllers

import play.api.mvc.{Request, Controller}
import org.intracer.wmua._
import play.api.data.Form
import play.api.data.Forms._
import scala.collection.mutable
import play.api.mvc.SimpleResult


object Gallery extends Controller with Secured {

  def pages = 15

  val Selected = "selected"

  val Filter = "filter"

  val UrlInProgress = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8e/Icon_tools.svg/120px-Icon_tools.svg.png"

  def list(rate: Int, page: Int = 1) = withAuth {
    username =>
      implicit request =>
        val user = User.byUserName(username)
        var files = userFiles(user).filter(_.rate == rate)

        val pageFiles: Seq[ImageWithRating] = files.slice((page - 1) * (filesPerPage(user) + 1), Math.min(page * (filesPerPage(user) + 1), files.size))
        Ok(views.html.gallery(user, pageFiles, page, Round.current(user), rate))
  }

  def large(rate: Int, index: Int) = withAuth {
    username =>
      implicit request =>

        show(index, username.trim, rate)
  }

  def userFiles(user: User): Seq[ImageWithRating] = {
    val round = Round.current(user)

    if (user.files.isEmpty) {
      user.files ++= Image.byUserImageWithRating(user, round.id)
    }
    user.files
  }

  def select(rate: Int, index: Int, select: Int) = withAuth {
    username =>
      implicit request =>

        val user = User.byUserName(username)

        val round = Contest.currentRound(user.contest)

        val files = userFiles(user).filter(_.rate == rate)
        val file = files(index)

        file.rate = select

        Selection.rate(pageId = file.pageId, juryid = user.id.toInt, round = round, rate = select)

        checkLargeIndex(rate, index, files)

        //show(index, username, rate)
  }

  def checkLargeIndex(rate: Int, index: Int, files: Seq[ImageWithRating]): SimpleResult = {
    val newIndex = if (index > files.size - 2)
      files.size - 2
    else index

    if (newIndex >= 0) {
      Redirect(routes.Gallery.large(rate, newIndex))
    } else {
      Redirect(routes.Gallery.list(rate, 1))
    }
  }

  def show(index: Int, username: String, rate: Int)(implicit request: Request[Any]): SimpleResult = {
    val user = User.byUserName(username)

    var files = userFiles(user)

    files = files.filter(_.rate == rate)

    val page = (index / filesPerPage(user)) + 1

    show2(index, files, user, rate, username, page)
  }


  def show2(index: Int, files: Seq[ImageWithRating], user: User, rate: Int, username: String,
            page: Int, region: Option[String] = None, round: Int = 1)
           (implicit request: Request[Any]): SimpleResult = {
    val extraRight = if (index - 2 < 0) 2 - index else 0
    val extraLeft = if (files.size < index + 3) index + 3 - files.size else 0

    val left = Math.max(0, index - 2)
    val right = Math.min(index + 3, files.size)
    val start = Math.max(0, left - extraLeft)
    var end = Math.min(files.size, right + extraRight)

    if (!region.isDefined) {
       Ok(views.html.large(User.byUserName(username), files, index, start, end, page, rate))
    } else {
      Ok(views.html.largeRound2(User.byUserName(username), files, index, start, end, region.get, round))
    }
  }

  def filesPerPage(user: User): Int = {
    userFiles(user).size / pages
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


//  def selectRound2(regionId: String, pageId: Long, round: Int) = withAuth {
//    username =>
//      implicit request =>
//        val user = User.byUserName(username)
//
//        val files = if (round > 1) {
//          Image.byUserImageWithRating(user, round - 1)
//        } else {
//          userFiles(user)
//        }
//
//        val filesInRegion = KOATUU.filesInRegion(files, regionId)
//
//        val file = files.find(_.pageId == pageId).get
//
//        val index = filesInRegion.indexOf(file)
//
//        val selection = Selection.rate(pageId = file.pageId, juryid = user.id.toInt, round = round)
//
//        Redirect(routes.Gallery.largeRound2(regionId, index, round))
//  }
//
//  def unselectRound2(regionId: String, pageId: Long, round: Int = 2) = withAuth {
//    username =>
//      implicit request =>
//        val user = User.byUserName(username)
//
//        val files = if (round > 1) {
//          Image.byUserImageWithRating(user, round - 1)
//        } else {
//          userFiles(user)
//        }
//
//        val filesInRegion = KOATUU.filesInRegion(files, regionId)
//
//        val file = files.find(_.pageId == pageId).get
//
//        val index = filesInRegion.indexOf(file)
//
//        Selection.destroy(pageId = file.pageId, juryid = user.id, round = round)
//
//        Redirect(routes.Gallery.largeRound2(regionId, index, round))
//  }


//  def selectWiki(file: String, user: User) {
//    var text = Global.w.getPageText(file)
//    if (!text.contains("WLM 2013 in Ukraine Round One " + user.fullname)) {
//      val newCat: String = s"[[Category:WLM 2013 in Ukraine Round One ${user.fullname}]]"
//      text += "\n" + newCat
//      Global.w.edit(file, text, s"Adding $newCat")
//    }
//  }
//
//  def deselectWiki(file: String, user: User) {
//    var text = Global.w.getPageText(file)
//    if (text.contains("WLM 2013 in Ukraine Round One " + user.fullname)) {
//      val newCat: String = s"\\Q[[Category:WLM 2013 in Ukraine Round One ${user.fullname}]]\\E"
//      text = text.replaceAll(newCat, "")
//      Global.w.edit(file, text, s"Removing [[Category:WLM 2013 in Ukraine Round One ${user.fullname}]]")
//    }
//  }

//  def round2(regionId: String, round: Int) = withAuth {
//    username =>
//      implicit request =>
//        val user = User.byUserName(username)
//
//        val files = if (round > 1) {
//          Image.byUserImageWithRating(user, round - 1)  // TODO round math
//        } else {
//          userFiles(user)
//        }
//
//        val filesInRegion = KOATUU.filesInRegion(files, regionId)
//
//        Ok(views.html.round2(user, filesInRegion, regionId, Round.current(user)))
//  }
//
//  def largeRound2(regionId: String, index: Int, round: Int, rate: Int) = withAuth {
//    username =>
//      implicit request =>
//
//        val user = User.byUserName(username)
//
//        val files = if (round > 1) {
//          Image.byUserImageWithRating(user, round - 1)  // TODO round math
//        } else {
//          userFiles(user)
//        }
//        val selected = mutable.SortedSet(Image.bySelectionSelected(round): _*)
//
//        val filesInRegion = KOATUU.filesInRegion(files, regionId)
//
//        show2(index, filesInRegion, user, rate, user.email, 0, Some(regionId), round)
//  }
