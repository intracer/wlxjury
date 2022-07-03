package db.scalikejdbc

import controllers.Greeting
import org.intracer.wmua.HasId

case class ContestJury(id: Option[Long],
                       name: String,
                       year: Int,
                       country: String,
                       images: Option[String],
                       categoryId: Option[Long] = None,
                       currentRound: Option[Long] = None,
                       monumentIdTemplate: Option[String] = None,
                       greeting: Greeting = Greeting(None, use = true),
                       campaign: Option[String] = None) extends HasId {
  //def localName = Messages("wiki.loves.earth." + country, year)(messages)
  def fullName = s"$name $year in $country"

  def getImages = images.getOrElse("Category:Images from " + name)
}
