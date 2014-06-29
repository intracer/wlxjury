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

  def totalRate:Double = if (ratedJurors == 0) 0 else rateSum.toDouble / ratedJurors

  def rateSum = selection.foldLeft(0)( _ + _.rate)

  def ratedJurors = selection.count(_.rate > 0)

  def rateString = if (ratedJurors == 0) "0" else s"${Formatter.fmt.format(totalRate)} ($rateSum / $ratedJurors)"

  def pageId = image.pageId

  def title = image.title

  def compare(that: ImageWithRating) =  (this.pageId - that.pageId).signum
}

object Formatter {
  val fmt = new DecimalFormat("0.00")
}
