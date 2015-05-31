package org.intracer.wmua

import db.scalikejdbc.{RoundJdbc, ImageJdbc, ContestJuryJdbc}
import scalikejdbc._
import org.joda.time.DateTime
import org.scalawiki.dto.Page

case class Round(id: Long,
                 number: Int,
                 name: Option[String],
                 contest: Int,
                 roles: Set[String] = Set("jury"),
                 distribution: Int,
                 rates: Rates = RoundJdbc.binaryRound,
                 limitMin: Option[Int],
                 limitMax: Option[Int],
                 recommended: Option[Int],
                 images: Seq[Page] = Seq.empty,
                 selected: Seq[Page] = Seq.empty,
                 createdAt: DateTime = DateTime.now,
                 deletedAt: Option[DateTime] = None,
                 active: Boolean = false,
                 optionalRate: Boolean = false,
                 juryOrgView: Boolean = false) {

  def jurors = User.findAllBy(sqls.in(User.u.roles, roles.toSeq).and.eq(User.u.contest, contest))

  def activeJurors = if (!optionalRate) _allJurors else _activeJurors

  lazy val _activeJurors = Selection.activeJurors(id)

  lazy val _allJurors = Selection.allJurors(id)


  def allImages = ImageJdbc.byRoundMerged(id.toInt)

  def description: String = name.flatMap(s => if (s.trim.isEmpty) None else Some(s)).fold(number.toString)(s => s)
}


case class Rates(id: Int, name: String, minRate: Int = 0, maxRate: Int = 1)

object Rates {

  val map = Map(1 -> "selected", 0 -> "unrated", -1 -> "rejected")

  val pairs: Seq[(Option[Int], String)] = Seq(Some(1) -> "selected", Some(0) -> "unrated", Some(-1) -> "rejected")


}

//case class Limits(min:Int, max:Int, recommended: Option[Int])

