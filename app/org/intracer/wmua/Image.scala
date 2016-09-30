package org.intracer.wmua

import java.text.DecimalFormat

case class Image(pageId: Long,
                 contest: Long,
                 title: String,
                 url: Option[String] = None,
                 pageUrl: Option[String] = None,
                 width: Int,
                 height: Int,
                 monumentId: Option[String],
                 description: Option[String] = None,
                 author: Option[String] = None
                  ) extends Ordered[Image] {

  def compare(that: Image) = (this.pageId - that.pageId).signum

  def region: Option[String] = monumentId.map(_.split("-")(0))

  def resizeTo(resizeToX: Int, resizeToY: Int): Int =
    ImageUtil.resizeTo(width, height, resizeToX, resizeToY)

  def resolutionStr = s"$width x $height"

  def mpxStr = ImageUtil.fmt.format(mpx)

  def mpx = width * height / 1000000.0

}

object ImageUtil {

  def fmt = new DecimalFormat("0.0")

  def resizeTo(w: Int, h: Int, resizeToX: Int, resizeToY: Int): Int = {
    val xRatio = w.toDouble / resizeToX
    val yRatio = h.toDouble / resizeToY

    Seq(resizeToX, (w / yRatio).toInt, w).min
  }


}



