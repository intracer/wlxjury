package org.intracer.wmua

import org.joda.time.DateTime
import scalikejdbc._

case class Comment(
  id: Long,
  userId: Int,
  username: String,
  round: Int,
  room: Int,
  createdAt: String,
  body: String)
{


}


object CommentJdbc extends SQLSyntaxSupport[Comment] {

  override val tableName = "comment"

  val c = CommentJdbc.syntax("c")

  def apply(c: SyntaxProvider[Comment])(rs: WrappedResultSet): Comment = apply(c.resultName)(rs)

  def apply(c: ResultName[Comment])(rs: WrappedResultSet): Comment = new Comment(
    id = rs.long(c.id),
    userId = rs.int(c.userId),
    username = rs.string(c.username),
    round = rs.int(c.round),
    room = rs.int(c.room),
    createdAt = rs.string(c.createdAt),//rs.timestamp(c.createdAt).toJodaDateTime,
    body = rs.string(c.body)
  )

  def create(userId: Int, username: String, round: Int, room: Int, body: String, createdAt: String = DateTime.now.toString)(implicit session: DBSession = autoSession): Comment = {
    val id = withSQL {
      insert.into(CommentJdbc).namedValues(
        column.userId -> userId,
        column.username -> username,
        column.round -> round,
        column.room -> room,
        column.body -> body,
        column.createdAt -> createdAt)
    }.updateAndReturnGeneratedKey().apply()

    Comment(id = id, userId = userId, username = username, round = round, room =  room, body = body, createdAt = createdAt)
  }

  def findAll()(implicit session: DBSession = autoSession): List[Comment] = withSQL {
    select.from(CommentJdbc as c)
      //      .where.append(isNotDeleted)
      .orderBy(c.id)
  }.map(CommentJdbc(c)).list().apply()

  def findByRound(round: Int)(implicit session: DBSession = autoSession): List[Comment] = withSQL {
    select.from(CommentJdbc as c)
      .where //.append(isNotDeleted)
      // .and
      .eq(c.round, round)
      .orderBy(c.id)
  }.map(CommentJdbc(c)).list().apply()

}