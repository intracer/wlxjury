package org.intracer.wmua.cmd

import db.scalikejdbc.ImageJdbc
import org.intracer.wmua.{ContestJury, Image}
import org.scalawiki.MwBot
import org.scalawiki.dto.cmd.Action
import org.scalawiki.dto.cmd.query.prop.{CategoryInfo, Prop}
import org.scalawiki.dto.cmd.query.{Query, TitlesParam}
import org.scalawiki.dto.{Namespace, Page}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class ImageInfoFromCategory(category: String, contest: ContestJury, commons: MwBot, max: Long = 0L) {
  def apply(): Future[Seq[Image]] = {

    val imageInfoQuery = imageInfoByGenerator(category,
      "categorymembers", "cm",
      namespaces = Set(Namespace.FILE),
      props = Set("timestamp", "user", "size", "url"),
      limit = Math.min(Math.max(max / 20, 200), 500).toString
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

  def imageInfoByGenerator(category: String,
                           generator: String,
                           generatorPrefix: String,
                           namespaces: Set[Int],
                           props: Set[String],
                           limit: String = "max"): Future[Seq[Page]] = {

    val context = Map("contestId" -> contest.id.getOrElse(0).toString, "max" -> max.toString)

    commons.page(category).withContext(context).imageInfoByGenerator(
      "categorymembers", "cm", namespaces = Set(Namespace.FILE), props = Set("timestamp", "user", "size", "url"), titlePrefix = None
    )
  }

}
