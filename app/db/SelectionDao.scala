package db

import org.intracer.wmua.{Selection, User}
import org.joda.time.DateTime

trait SelectionDao {

  def byUser(user: User, roundId: Long): Seq[Selection]

  def byUserSelected(user: User, roundId: Long): Seq[Selection]

  def byRoundSelected(roundId: Long): Seq[Selection]

  def byRoundAndImageWithJury(roundId: Long, imageId: Long): Seq[(Selection, User)]

  def byRound(roundId: Long): Seq[Selection]

  def byUserNotSelected(user: User, roundId: Long): Seq[Selection]

  def find(id: Long): Option[Selection]

  def findAll(): List[Selection]

  def countAll(): Long

  def create(pageId: Long,
             rate: Int,
             fileid: String,
             juryId: Int,
             round: Int,
             createdAt: DateTime = DateTime.now
              ): Selection

  def batchInsert(selections: Seq[Selection])

  def destroy(pageId: Long, juryId: Long, round: Long): Unit

  def rate(pageId: Long, juryId: Long, round: Long, rate: Int = 1): Unit

  def activeJurors(roundId: Long): Int

  def allJurors(roundId: Long): Int

}
