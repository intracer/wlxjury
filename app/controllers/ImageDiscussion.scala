package controllers

import org.intracer.wmua.{CommentJdbc, Round}
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

  def addComment(pageId: Long, region: String = "all", rate: Option[Int], module: String, round: Option[Int]) = withAuth {
    user =>
      implicit request =>
        val roundId = round.getOrElse(Round.current(user).id.toInt)

        editCommentForm.bindFromRequest.fold(
          formWithErrors => // binding failure, you retrieve the form containing errors,
            Redirect(routes.Gallery.large(user.id.toInt, pageId, region, roundId, rate, module)),
          commentBody => {
            CommentJdbc.create(user.id.toInt, user.fullname, roundId, pageId, commentBody.text)
            Redirect(routes.Gallery.large(user.id.toInt, pageId, region, roundId, rate, module).url.concat("#comments"))
          }
    )
  }


}
