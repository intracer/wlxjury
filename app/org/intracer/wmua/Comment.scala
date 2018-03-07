package org.intracer.wmua

import java.time.ZonedDateTime

import scalikejdbc._

case class Comment(
  id: Long,
  userId: Long,
  username: String,
  round: Long,
  contestId: Option[Long],
  room: Long,
  createdAt: String,
  body: String)

object CommentJdbc extends SQLSyntaxSupport[Comment] {

  override val tableName = "comment"

  val c = CommentJdbc.syntax("c")

  def apply(c: SyntaxProvider[Comment])(rs: WrappedResultSet): Comment = apply(c.resultName)(rs)

  def apply(c: ResultName[Comment])(rs: WrappedResultSet): Comment = new Comment(
    id = rs.long(c.id),
    userId = rs.int(c.userId),
    username = rs.string(c.username),
    round = rs.int(c.round),
    contestId = rs.longOpt(c.contestId),
    room = rs.int(c.room),
    createdAt = rs.string(c.createdAt),
    body = rs.string(c.body)
  )

  def create(userId: Long, username: String, round: Long, contestId: Option[Long], room: Long, body: String,
             createdAt: String = ZonedDateTime.now.toString)(implicit session: DBSession = autoSession): Comment = {
    val id = withSQL {
      insert.into(CommentJdbc).namedValues(
        column.userId -> userId,
        column.username -> username,
        column.round -> round,
        column.room -> room,
        column.contestId -> contestId,
        column.body -> body,
        column.createdAt -> createdAt)
    }.updateAndReturnGeneratedKey().apply()

    Comment(id = id, userId = userId, username = username, round = round, contestId = contestId, room =  room, body = body,
      createdAt = createdAt)
  }

  def findAll()(implicit session: DBSession = autoSession): List[Comment] = withSQL {
    select.from(CommentJdbc as c)
      //      .where.append(isNotDeleted)
      .orderBy(c.id)
  }.map(CommentJdbc(c)).list().apply()

  def findByRound(round: Long)(implicit session: DBSession = autoSession): List[Comment] = withSQL {
    select.from(CommentJdbc as c)
      .where //.append(isNotDeleted)
      // .and
      .eq(c.round, round)
      .orderBy(c.id)
  }.map(CommentJdbc(c)).list().apply()

  def findByRoundAndSubject(round: Long, subject: Long)(implicit session: DBSession = autoSession): List[Comment] = withSQL {
    select.from(CommentJdbc as c)
      .where //.append(isNotDeleted)
      // .and
      .eq(c.round, round).and
      .eq(c.room, subject)
      .orderBy(c.id)
  }.map(CommentJdbc(c)).list().apply()

  def findBySubject(subject: Long)(implicit session: DBSession = autoSession): List[Comment] = withSQL {
    select.from(CommentJdbc as c)
      .where //.append(isNotDeleted)
      // .and
      .eq(c.room, subject)
      .orderBy(c.id)
  }.map(CommentJdbc(c)).list().apply()

  def findBySubjectAndContest(subject: Long, contestId: Long)(implicit session: DBSession = autoSession): List[Comment] = withSQL {
    select.from(CommentJdbc as c)
      .where
      .eq(c.room, subject).and
      .eq(c.contestId, contestId)
      .orderBy(c.id)
  }.map(CommentJdbc(c)).list().apply()
}