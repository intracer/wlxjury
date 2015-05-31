package org.intracer.wmua

import _root_.play.api.i18n.Messages

case class ContestJury(
                        id: Option[Long],
                        year: Int,
                        country: String,
                        images: Option[String],
                        currentRound: Int,
                        monumentIdTemplate:Option[String],
                        messages: Messages) {
  def name = Messages("wiki.loves.earth." + country, year)(messages)

  def getImages = images.getOrElse("Category:Images from " + name)
}

