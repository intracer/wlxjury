package org.intracer.wmua

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


  def pageId = image.pageId

  def title = image.title

  def compare(that: ImageWithRating) =  (this.pageId - that.pageId).signum
}
