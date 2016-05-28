package org.intracer.wmua.cmd

import db.scalikejdbc.ImageJdbc
import org.intracer.wmua.{ContestJury, Image}
import org.scalawiki.MwBot
import org.scalawiki.dto.Namespace
import org.scalawiki.dto.cmd.Action
import org.scalawiki.dto.cmd.query.prop.{CategoryInfo, Prop}
import org.scalawiki.dto.cmd.query.{Query, TitlesParam}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


case class ImageInfoFromCategory(category: String, contest: ContestJury, commons: MwBot) {
  def apply(): Future[Seq[Image]] = {
    val query = commons.page(category)

    val imageInfoQuery = query.imageInfoByGenerator(
      "categorymembers", "cm",
      namespaces = Set(Namespace.FILE),
      props = Set("timestamp", "user", "size", "url"),
      titlePrefix = None
    )

    for (pages <- imageInfoQuery) yield
      pages.flatMap(page => ImageJdbc.fromPage(page, contest))

  }

  def numberOfImages: Future[Long] = {
    val query = Action(Query(TitlesParam(Seq(category)), Prop(CategoryInfo)))
    commons.run(query).map {
      _.head.categoryInfo.map(_.files).getOrElse(0L)
    }
  }
}
