package db

import org.intracer.wmua.{Round, User}
import org.joda.time.DateTime

trait RoundDao {

  def activeRounds(contestId: Int)

  def current(user: User)

  def findAll(): List[Round]

  def findByContest(contest: Long)

  def find(id: Long): Option[Round]

  def create(number: Int,
             name: Option[String],
             contest: Int,
             roles: String,
             distribution: Int,
             rates: Int,
             limitMin: Option[Int],
             limitMax: Option[Int],
             recommended: Option[Int],
             createdAt: DateTime = DateTime.now): Round


  def updateRound(id: Long, round: Round)

  def countByContest(contest: Int): Int

}
