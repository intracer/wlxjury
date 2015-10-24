package org.intracer.wmua

import org.specs2.mutable.Specification

class ResizeSpec extends Specification {

  "resize" should {
    "be same" in {
      val (imageX, imageY) = (320, 200)
      val (boxX, boxY) = (320, 200)

      val px = ImageUtil.resizeTo(imageX, imageY, boxX, boxY)
      px === 320
    }

    "divideBy2" in {
      val (imageX, imageY) = (640, 400)
      val (boxX, boxY) = (320, 200)

      val px = ImageUtil.resizeTo(imageX, imageY, boxX, boxY)
      px === 320
    }

    "vertical divide by 2" in {
      val (imageX, imageY) = (400, 200)
      val (boxX, boxY) = (320, 200)

      val px = ImageUtil.resizeTo(imageX, imageY, boxX, boxY)
      px === 320
    }
  }

}
