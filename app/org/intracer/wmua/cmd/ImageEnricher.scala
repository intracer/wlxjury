package org.intracer.wmua.cmd

import org.intracer.wmua.Image

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object ImageEnricher {

  def zipWithRevData(images: Seq[Image], revData: Seq[Image]): Seq[Image] = {

    val revisionsById = revData.groupBy(_.pageId)

    images.map { i =>
      val revOpt = revisionsById(i.pageId).headOption
      revOpt.fold(i)(r => i.copy(
        monumentId = r.monumentId,
        description = r.description))
    }
  }

  def zipWithRevData(images: Future[Seq[Image]],
                     revData: Future[Seq[Image]]
                    ): Future[Seq[Image]] =
    for (i <- images;
         r <- revData)
      yield zipWithRevData(i, r)

}




