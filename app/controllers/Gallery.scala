package controllers

import play.api.mvc.{Request, SimpleResult, Controller}
import org.intracer.wmua.User
import play.api.data.Form
import play.api.data.Forms._
import play.api.templates.Html
import java.util
import org.wikipedia.Wiki
import scala.collection.JavaConverters._
object Gallery extends Controller with Secured {

  val pages = 15

  val urlInProgress = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8e/Icon_tools.svg/120px-Icon_tools.svg.png"

  def list(page: Int = 1) = withAuth {
    username =>
      implicit request =>
        val user = User.byUserName.get(username).get

        val files = userFiles(user)
        val pageFiles: Seq[String] = files.slice((page - 1) * filesPerPage(user), Math.min(page * filesPerPage(user), files.size))
        Ok(views.html.gallery(user, pageFiles, user.selected, page))
  }

  def large(index: Int) = withAuth {
    username =>
      implicit request =>

        show(index, username)
  }

  def userFiles(user: User):Seq[String] = {
     if (user.files.isEmpty) {
       user.files ++= util.Arrays.asList[String](Global.w.getCategoryMembers("Category:WLM_2013_in_Ukraine_Round_Zero_-_Category_" + user.hash, Wiki.FILE_NAMESPACE): _*).asScala
     }
    user.files
  }


  def select(index: Int) = withAuth {
    username =>
      implicit request =>

        val user = User.byUserName.get(username).get
        val selected = user.selected

        if (selected.contains(index)) {
          val file = userFiles(user)(index)
          var text = Global.w.getPageText(file)
          if (text.contains("WLM 2013 in Ukraine Round One " + user.fullname)) {
            val newCat: String = s"\\Q[[Category:WLM 2013 in Ukraine Round One ${user.fullname}]]\\E"
            text = text.replaceAll(newCat, "")
            Global.w.edit(file, text, s"Removing [[Category:WLM 2013 in Ukraine Round One ${user.fullname}]]")
          }
          selected -= index
        }
        else {
          val file = userFiles(user)(index)
          var text = Global.w.getPageText(file)
          if (!text.contains("WLM 2013 in Ukraine Round One " + user.fullname)) {
            val newCat: String = s"[[Category:WLM 2013 in Ukraine Round One ${user.fullname}]]"
            text += "\n" + newCat
            Global.w.edit(file, text, s"Adding $newCat")
          }
          selected += index
        }

        show(index, username)

  }


  def show(index: Int, username: String)(implicit request: Request[Any]): SimpleResult[Html] = {
    val user = User.byUserName.get(username).get

    val files = userFiles(user)

    val extraRight = if (index - 2 < 0) 2 - index else 0
    val extraLeft = if (files.size < index + 3) index + 3 - files.size else 0

    val page = (index / filesPerPage(user)) + 1

    val left = Math.max(0, index - 2)
    val right = Math.min(index + 3, files.size)
    val start = Math.max(0, left - extraLeft)
    var end = Math.min(files.size, right + extraRight)

    val selected = user.selected

    Ok(views.html.large(loginForm, User.byUserName.get(username).get, files, selected, index, start, end, page))
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
