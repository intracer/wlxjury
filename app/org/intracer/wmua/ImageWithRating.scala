package org.intracer.wmua

case class ImageWithRating(image: Image, selection: Selection) extends Ordered[ImageWithRating]{
  def unSelect() {
    selection.rate = -1
  }

  def select() {
    selection.rate = 1
  }

  def isSelected: Boolean = selection.rate > 0

  def pageId = image.pageId

  def title = image.title

  def compare(that: ImageWithRating) =  (this.pageId - that.pageId).signum
}
