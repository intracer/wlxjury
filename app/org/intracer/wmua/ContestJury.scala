package org.intracer.wmua

case class ContestJury(
                        id: Option[Long],
                        name: String,
                        year: Int,
                        country: String,
                        images: Option[String],
                        currentRound: Option[Long] = None,
                        monumentIdTemplate: Option[String] = None) {
  //def localName = Messages("wiki.loves.earth." + country, year)(messages)

  def getImages = images.getOrElse("Category:Images from " + name)
}

