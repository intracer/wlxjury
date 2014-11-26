package org.intracer.wmua

import java.text.DecimalFormat

case class ImageWithRating(image: Image, selection: Seq[Selection]) extends Ordered[ImageWithRating]{
  def unSelect() {
    selection.head.rate = -1
  }

  def select() {
    selection.head.rate = 1
  }

  def isSelected: Boolean = selection.head.rate > 0

  def isRejected: Boolean = selection.head.rate < 0

  def isUnrated: Boolean = selection.head.rate == 0

  def rate = selection.head.rate

  def rate_=(rate:Int) {
    selection.head.rate = rate
  }

  def totalRate(round: Round):Double = if (ratedJurors(round) == 0) 0.0 else rateSum.toDouble / ratedJurors(round)

  def rateSum = selection.foldLeft(0)( _ + _.rate)

  def ratedJurors(round: Round):Int = if (round.optionalRate) round.activeJurors else selection.count(_.rate > 0)

  def rateString(round: Round) = if (ratedJurors(round) == 0) "0" else s"${Formatter.fmt.format(totalRate(round))} ($rateSum / ${ratedJurors(round)})"

  def pageId = image.pageId

  def title = image.title

  def compare(that: ImageWithRating) =  (this.pageId - that.pageId).signum
}

object Formatter {
  val fmt = new DecimalFormat("0.00")
}
