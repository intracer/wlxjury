package org.intracer.wmua.cmd

import db.scalikejdbc.SelectionJdbc
import org.intracer.wmua.{Round, Image, User, Selection}
import org.joda.time.DateTime

case class DistributeImages(round: Round, images: Seq[Image], jurors: Seq[User]) {

  def apply() = {
    val selection: Seq[Selection] = round.distribution match {
      case 0 =>
        jurors.flatMap { juror =>
          images.map(img => new Selection(0, img.pageId, 0, juror.id.get, round.id.get, DateTime.now))
        }
      case x if x > 0 =>
        images.zipWithIndex.flatMap {
          case (img, i) =>
            (0 to x - 1).map(j =>
              new Selection(0, img.pageId, 0, jurors((i + j) % jurors.size).id.get, round.id.get, DateTime.now)
          )
        }
    }
    println("saving selection: " + selection.size)
    SelectionJdbc.batchInsert(selection)
    println(s"saved selection")
  }

}
