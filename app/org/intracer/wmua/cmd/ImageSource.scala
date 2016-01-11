package org.intracer.wmua.cmd

import org.intracer.wmua.Image

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ImageSource {

}

object ImageEnricher {

  def zip(images: Seq[Image],
          extraData: Seq[Image],
          extraFun: (Image, Image) => Image) =
    images.zip(extraData).map(extraFun.tupled)

  def zipWithRevData(images: Seq[Image],
                     revData: Seq[Image]): Seq[Image] =
    zip(images, revData,
      (i, r) => i.copy(monumentId = r.monumentId, description = r.description)
    )

  def zipWithRevData(images: Future[Seq[Image]],
                     revData: Future[Seq[Image]]): Future[Seq[Image]] =
    for (i <- images; r <- revData)
      yield zipWithRevData(i, r)

}




