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

  def addComment(pageId: Long, region: String = "all", rate: Option[Int], module: String, round: Option[Long]) = withAuth() {
    user =>
      implicit request =>
        val roundId = round.getOrElse(RoundJdbc.current(user).flatMap(_.id).get)

        editCommentForm.bindFromRequest.fold(
          formWithErrors => // binding failure, you retrieve the form containing errors,
            Redirect(routes.Gallery.large(user.id.get, pageId, region, roundId, rate, module)),
          commentBody => {
            CommentJdbc.create(user.id.get, user.fullname, roundId, pageId, commentBody.text)
            Redirect(routes.Gallery.large(user.id.get, pageId, region, roundId, rate, module).url.concat("#comments"))
          }
    )
  }


}
