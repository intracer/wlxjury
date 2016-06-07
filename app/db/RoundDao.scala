package db

import org.intracer.wmua.{Round, User}
import org.joda.time.DateTime

trait RoundDao {

  def activeRounds(contestId: Long): Seq[Round]

  def current(user: User): Option[Round]

  def findAll(): Seq[Round]

  def findByContest(contest: Long): Seq[Round]

  def find(id: Long): Option[Round]

  def create(number: Int,
             name: Option[String],
             contest: Long,
             roles: String,
             distribution: Int,
             rates: Int,
             limitMin: Option[Int],
             limitMax: Option[Int],
             recommended: Option[Int],
             createdAt: DateTime = DateTime.now,
             minMpx: Option[Int]
            ): Round

  def create(round: Round): Round

  def updateRound(id: Long, round: Round)

  def setActive(id: Long, active: Boolean)

  def countByContest(contest: Long): Int

}
