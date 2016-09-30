package org.intracer.wmua

import db.scalikejdbc._
import scalikejdbc._
import org.joda.time.DateTime
import org.scalawiki.dto.Page

case class Round(id: Option[Long],
                 number: Int,
                 name: Option[String] = None,
                 contest: Long,
                 roles: Set[String] = Set("jury"),
                 distribution: Int = 0,
                 rates: Rates = Round.binaryRound,
                 limitMin: Option[Int] = None,
                 limitMax: Option[Int] = None,
                 recommended: Option[Int] = None,
                 images: Seq[Page] = Seq.empty,
                 selected: Seq[Page] = Seq.empty,
                 createdAt: DateTime = DateTime.now,
                 deletedAt: Option[DateTime] = None,
                 active: Boolean = false,
                 optionalRate: Boolean = false,
                 juryOrgView: Boolean = false,
                 minMpx: Option[Int] = None,
                 previous: Option[Long] = None,
                 prevSelectedBy: Option[Int] = None,
                 prevMinAvgRate: Option[Int] = None,
                 category: Option[String] = None,
                 categoryClause: Option[Int] = None,
                 regions: Option[String] = None,
                 hasCriteriaRate: Boolean = false) {

  def jurors = UserJdbc.findAllBy(sqls.in(UserJdbc.u.roles, roles.toSeq).and.eq(UserJdbc.u.contest, contest))

  def activeJurors = if (!optionalRate) _allJurors else _activeJurors

  lazy val _activeJurors = SelectionJdbc.activeJurors(id.get)

  lazy val _allJurors = SelectionJdbc.allJurors(id.get)

  def allImages = ImageJdbc.byRoundMerged(id.get)

  def description: String = name.flatMap(s => if (s.trim.isEmpty) None else Some(s)).fold(number.toString)(s => s)

  def isBinary = rates.id == Round.binaryRound.id

  def regionIds = regions.map(_.split(",").toSeq).getOrElse(Seq.empty[String])

}

object Round {
  val binaryRound = new Rates(1, "+/-", -1, 1)
  val rateRounds = (3 to 20).map(i => new Rates(i, s"1-$i rating", 1, i))

  val rates = Seq(binaryRound) ++ rateRounds

  val ratesById = rates.groupBy(_.id).mapValues(_.head)

}


case class Rates(id: Int, name: String, minRate: Int = 0, maxRate: Int = 1)

object Rates {

  val map = Map(1 -> "selected", 0 -> "unrated", -1 -> "rejected")

  val pairs: Seq[(Option[Int], String)] = Seq(Some(1) -> "selected", Some(0) -> "unrated", Some(-1) -> "rejected")


}