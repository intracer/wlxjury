package org.intracer.wmua

import controllers.Greeting

case class ContestJury(
                        id: Option[Long],
                        name: String,
                        year: Int,
                        country: String,
                        images: Option[String],
                        currentRound: Option[Long] = None,
                        monumentIdTemplate: Option[String] = None,
                        greeting: Greeting = Greeting(None, true)) {
  //def localName = Messages("wiki.loves.earth." + country, year)(messages)
  def fullName = s"$name $year in $country"

  def getImages = images.getOrElse("Category:Images from " + name)
}

