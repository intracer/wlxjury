package org.intracer.wmua

case class ContestJury(
                        id: Option[Long],
                        name: String,
                        year: Int,
                        country: String,
                        images: Option[String],
                        currentRound: Long,
                        monumentIdTemplate: Option[String]) {
  //def localName = Messages("wiki.loves.earth." + country, year)(messages)

  def getImages = images.getOrElse("Category:Images from " + name)
}

