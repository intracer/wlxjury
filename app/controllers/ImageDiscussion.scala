package controllers

import db.scalikejdbc.RoundJdbc
import org.intracer.wmua.CommentJdbc
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.Controller


case class CommentBody(id: Long, text: String)

object ImageDiscussion extends Controller with Secured {

  val editCommentForm = Form(
    mapping(
      "id" -> longNumber(),
      "text" -> text()
    )(CommentBody.apply)(CommentBody.unapply)
  )


  //  def list(pageId: Long) = withAuth {
  //    user =>
  //      implicit request =>
  //        val round = Round.current(user)
  //
  //        CommentJdbc.findByRound(round.id.toInt)
  //        Ok(views.html.large("Chat", user, user.id.toInt, user, messages, user.files, Seq(round), gallery = true))
  //
  //  }

  def addComment(pageId: Long, region: String = "all", rate: Option[Int], module: String) = withAuth {
    user =>
      implicit request =>
        val round = RoundJdbc.current(user)

        editCommentForm.bindFromRequest.fold(
          formWithErrors => // binding failure, you retrieve the form containing errors,
            Redirect(routes.Gallery.large(user.id.get, pageId, region, round.id.get, rate, module)),
          commentBody => {
            CommentJdbc.create(user.id.get, user.fullname, round.id.get, pageId, commentBody.text)
            Redirect(routes.Gallery.large(user.id.get, pageId, region, round.id.get, rate, module).url.concat("#comments"))
          }
    )
  }


}
