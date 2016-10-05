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

    "do not upscale" in {
      val (imageX, imageY) = (320, 200)
      val (boxX, boxY) = (640, 480)

      val px = ImageUtil.resizeTo(imageX, imageY, boxX, boxY)
      px === 320
    }
  }

  "resize to height" should {

    "be same" in {
      val (imageX, imageY) = (320, 200)
      val (boxX, boxY) = (320, 200)

      val px = ImageUtil.resizeTo(imageX, imageY, boxY)
      px === 320
    }

    "divideBy2" in {
      val (imageX, imageY) = (640, 400)
      val (boxX, boxY) = (320, 200)

      val px = ImageUtil.resizeTo(imageX, imageY, boxY)
      px === 320
    }

    "vertical no divide by 2" in {
      val (imageX, imageY) = (400, 200)
      val (boxX, boxY) = (320, 200)

      val px = ImageUtil.resizeTo(imageX, imageY, boxY)
      px === 400
    }

    "do not upscale" in {
      val (imageX, imageY) = (320, 200)
      val (boxX, boxY) = (640, 480)

      val px = ImageUtil.resizeTo(imageX, imageY, boxY)
      px === 320
    }
  }

}
