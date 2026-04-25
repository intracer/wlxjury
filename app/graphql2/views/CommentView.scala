package graphql2.views

import org.intracer.wmua.Comment

case class CommentView(
  id:          String,
  userId:      String,
  username:    String,
  roundId:     String,
  contestId:   Option[String],
  imagePageId: String,
  body:        String,
  createdAt:   String
)

object CommentView {
  def from(c: Comment): CommentView = CommentView(
    id          = c.id.toString,
    userId      = c.userId.toString,
    username    = c.username,
    roundId     = c.roundId.toString,
    contestId   = c.contestId.map(_.toString),
    imagePageId = c.room.toString,
    body        = c.body,
    createdAt   = c.createdAt
  )
}
