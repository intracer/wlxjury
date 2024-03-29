package controllers

import db.scalikejdbc.Round
import org.intracer.wmua.CommentJdbc
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc.{AbstractController, ControllerComponents}

import javax.inject.Inject

case class CommentBody(id: Long, text: String)

class ImageDiscussionController @Inject()(cc: ControllerComponents)
    extends Secured(cc)
      with I18nSupport {

  val editCommentForm = Form(
    mapping(
      "id" -> longNumber(),
      "text" -> text()
    )(CommentBody.apply)(CommentBody.unapply)
  )

  def addComment(pageId: Long,
                 region: String = "all",
                 rate: Option[Int],
                 module: String,
                 round: Option[Long],
                 contestId: Option[Long]) = withAuth() {
    user => implicit request =>
      val roundId = round.get

      editCommentForm.bindFromRequest().fold(
        formWithErrors => // binding failure, you retrieve the form containing errors,
          Redirect(
            routes.LargeViewController
              .large(user.getId, pageId, region, roundId, rate, module)),
        commentBody => {
          CommentJdbc.create(user.getId,
                             user.fullname,
                             roundId,
                             contestId.orElse(user.contestId),
                             pageId,
                             commentBody.text)
          Redirect(
            routes.LargeViewController
              .large(user.getId, pageId, region, roundId, rate, module)
              .url
              .concat("#comments"))
        }
      )
  }

}
