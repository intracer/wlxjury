package org.intracer.wmua.cmd

import org.intracer.wmua.{ContestJury, Image}
import org.scalawiki.MwBot
import org.scalawiki.dto.Page
import org.scalawiki.dto.cmd.Action
import org.scalawiki.dto.cmd.query.list.ListArgs
import org.scalawiki.dto.cmd.query.{Generator, Query}
import org.scalawiki.dto.cmd.query.prop.rvprop.RvProp
import org.scalawiki.dto.cmd.query.prop.{Info, Prop, Revisions, RvPropArgs}
import org.scalawiki.query.DslQuery
import org.scalawiki.wikitext.TemplateParser

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class ImageTextFromCategory(
                                  category: String,
                                  contest: ContestJury,
                                  monumentIdTemplate: Option[String],
                                  commons: MwBot,
                                  max: Long) {

  def apply(): Future[Seq[Image]] = {

    val future = revisionsByGenerator(category, "categorymembers", "cm",
      Set.empty, Set("content", "timestamp", "user", "comment"), limit = "50")

    future.map {
      pages =>

        val images = pages.map(
          page =>
            new Image(page.id.get, contest.id.get, page.title, "", "", 0, 0, None, None)
        )

        val ids: Seq[String] = monumentIdTemplate.fold(Array.fill(pages.size)("").toSeq)(t => monumentIds(pages, t))

        val descrs: Seq[String] = descriptions(pages)

        val imagesWithIds = images.zip(ids).map {
          case (image, id) => image.copy(monumentId = Some(id))
        }

        val imagesWithDescr = imagesWithIds.zip(descrs).map {
          case (image, descr) => image.copy(description = Some(descr))
        }

        imagesWithDescr
    }
  }

  def monumentIds(pages: Seq[Page], monumentIdTemplate: String): Seq[String] = {
    pages.sortBy(_.id).map {
      page =>
        page.text.flatMap(text => defaultParam(text, monumentIdTemplate))
          .map(id => if (id.length < 100) id else id.substring(0, 100)).getOrElse("")
    }
  }

  def descriptions(pages: Seq[Page]): Seq[String] = {
    pages.sortBy(_.id).map {
      page =>
        page.text.flatMap(text => namedParam(text, "Information", "description")).getOrElse("")
    }
  }

  def defaultParam(text: String, templateName: String): Option[String] =
    TemplateParser.parseOne(text, Some(templateName)).flatMap(_.getParamOpt("1"))

  def namedParam(text: String, templateName: String, paramName: String): Option[String] =
    TemplateParser.parseOne(text, Some(templateName)).flatMap(_.getParamOpt(paramName))

  def revisionsByGenerator(category: String,
                                     generator: String,
                                     generatorPrefix: String,
                                     namespaces: Set[Int],
                                     props: Set[String],
                                     limit: String): Future[Seq[Page]] = {

    val pageId: Option[Long] = None
    val title = Some(category)

    val action = Action(Query(
      Prop(
        Info(),
        Revisions(RvProp(RvPropArgs.byNames(props.toSeq): _*))
      ),
      Generator(ListArgs.toDsl(generator, title, pageId, namespaces, Some(limit)))
    ))

    new DslQuery(action, commons,
      Map("contestId" -> contest.id.getOrElse(0).toString, "max" -> max.toString)
    ).run()
  }


}
