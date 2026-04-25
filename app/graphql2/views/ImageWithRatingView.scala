package graphql2.views

import org.intracer.wmua.ImageWithRating

case class ImageWithRatingView(
  image:      ImageView,
  selections: List[SelectionView],
  totalRate:  Double,
  rateSum:    Int,
  rank:       Option[Int]
)

object ImageWithRatingView {
  def from(iwr: ImageWithRating): ImageWithRatingView = {
    val sels = iwr.selection.toList
    val totalRate =
      if (sels.isEmpty) 0.0
      else sels.map(_.rate).sum.toDouble / sels.count(_.rate > 0).max(1)
    ImageWithRatingView(
      image      = ImageView.from(iwr.image),
      selections = sels.map(SelectionView.from),
      totalRate  = totalRate,
      rateSum    = iwr.rateSum,
      rank       = iwr.rank
    )
  }
}
