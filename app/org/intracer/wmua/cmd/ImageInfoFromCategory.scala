package org.intracer.wmua.cmd

import db.scalikejdbc.ImageJdbc
import org.intracer.wmua.{ContestJury, Image}
import org.scalawiki.MwBot
import org.scalawiki.dto.cmd.Action
import org.scalawiki.dto.cmd.query.list.ListArgs
import org.scalawiki.dto.cmd.query.prop.{CategoryInfo, ImageInfo, Prop}
import org.scalawiki.dto.cmd.query.{Generator, Query, TitlesParam}
import org.scalawiki.dto.{Namespace, Page}
import org.scalawiki.query.DslQuery

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


case class ImageInfoFromCategory(category: String, contest: ContestJury, commons: MwBot) {
  def apply(): Future[Seq[Image]] = {

    val imageInfoQuery = imageInfoByGenerator(category,
      "categorymembers", "cm",
      namespaces = Set(Namespace.FILE),
      props = Set("timestamp", "user", "size", "url")
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
    import org.scalawiki.dto.cmd.query.prop.iiprop._

    val pageId: Option[Long] = None
    val title = Some(category)

    val action = Action(Query(
      Prop(
        ImageInfo(
          IiProp(IiPropArgs.byNames(props.toSeq): _*)
        )
      ),
      Generator(ListArgs.toDsl(generator, title, pageId, namespaces, Some(limit)))
    ))

    new DslQuery(action, commons,
      Map("contestId" -> contest.id.getOrElse(0).toString)
    ).run()
  }

}
