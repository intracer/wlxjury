package org.intracer.wmua

import java.text.DecimalFormat

case class ImageWithRating(image: Image, selection: Seq[Selection], countFromDb: Int = 0) extends Ordered[ImageWithRating] {

  val ownJuryRating = new OwnRating(selection.headOption.getOrElse(new Selection(0, image.pageId, 0, 0, 0)))

  def unSelect(): Unit =
    ownJuryRating.unSelect()

  def select(): Unit =
    ownJuryRating.select()


  def isSelected: Boolean = ownJuryRating.isSelected

  def isRejected: Boolean = ownJuryRating.isRejected

  def isUnrated: Boolean = ownJuryRating.isUnrated

  def rate = ownJuryRating.rate

  def rate_=(rate:Int) {
    ownJuryRating.rate = rate
  }

  def totalRate(round: Round):Double =
    if (selection.size == 1 && selection.head.juryId != 0)
      selection.head.rate
    else
    if (ratedJurors(round) == 0)
      0.0
    else
      rateSum.toDouble / ratedJurors(round)

  def rateSum = selection.foldLeft(0)( _ + _.rate)

  def ratedJurors(round: Round):Int =
   if (round.rates.id == 1) {
     round._allJurors
   } else

   if (selection.head.juryId == 0 && !round.optionalRate)
     countFromDb
    else
    if (selection.size == 1 && selection.head.juryId != 0)
      1
    else if (round.optionalRate)
      round.activeJurors
    else selection.count(_.rate > 0)

  def rateString(round: Round) = if (ratedJurors(round) == 0) "0" else s"${Formatter.fmt.format(totalRate(round))} ($rateSum / ${ratedJurors(round)})"

  def pageId = image.pageId

  def title = image.title

  def compare(that: ImageWithRating) =  (this.pageId - that.pageId).signum
}

class Rating

class OneJuryRating(val selection: Selection) extends Rating {
  def isSelected: Boolean = selection.rate > 0

  def isRejected: Boolean = selection.rate < 0

  def isUnrated: Boolean = selection.rate == 0

  def rate = selection.rate
}

class OwnRating(selection: Selection) extends OneJuryRating(selection) {

  def unSelect() {
    selection.rate = -1
  }

  def select() {
    selection.rate = 1
  }

  def rate_=(rate:Int) {
    selection.rate = rate
  }

}

class TotalRating extends Rating


object Formatter {
  val fmt = new DecimalFormat("0.00")
}
