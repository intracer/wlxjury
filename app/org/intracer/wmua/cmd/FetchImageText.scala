package org.intracer.wmua.cmd

import org.intracer.wmua.{ContestJury, Image}
import org.scalawiki.MwBot
import org.scalawiki.dto.Page
import org.scalawiki.wikitext.TemplateParser

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import FetchImageText._

case class FetchImageText(source: String,
                          contest: ContestJury,
                          monumentIdTemplate: Option[String],
                          commons: MwBot,
                          max: Long = 0L) {

  def generatorParams:(String, String) = {
    if (source.toLowerCase.startsWith("category:")) {
      ("categorymembers", "cm")
    } else if (source.toLowerCase.startsWith("template:")) {
      ("embeddedin", "ei")
    }
    else {
      ("images", "im")
    }
  }

  def apply(): Future[Seq[Image]] = {

    val (generator, prefix) = generatorParams

    val future = revisionsByGenerator(source, generator, prefix,
      Set.empty, Set("content", "timestamp", "user", "comment"), limit = "50")

    future.map {
      pages =>

        val images = pages.sortBy(_.id).map(
          page =>
            Image(page.id.get, page.title, None, None, 0, 0, None, None)
        )

        val ids: Seq[String] = monumentIdTemplate.fold(Array.fill(pages.size)("").toSeq)(t => monumentIds(pages, t))

        val authorsSeq: Seq[String] = authors(pages)

        val descrs: Seq[String] = descriptions(pages)

        val imagesWithIds = images.zip(ids).map {
          case (image, id) => image.copy(monumentId = Some(id))
        }

        val imagesWithDescr = imagesWithIds.zip(descrs).map {
          case (image, descr) => image.copy(description = Some(descr))
        }

        val imagesWithAuthor = imagesWithDescr.zip(authorsSeq).map {
          case (image, author) => image.copy(author = Some(author))
        }

        imagesWithAuthor
    }
  }

  def monumentIds(pages: Seq[Page], monumentIdTemplate: String): Seq[String] = {
    pages.sortBy(_.id).map {
      page =>
        page.text.flatMap(text => defaultParam(text, monumentIdTemplate))
          .map(id => if (id.length < 100) id else id.substring(0, 100)).getOrElse("")
    }
  }

  def descriptions(pages: Seq[Page], existingPageIds: Set[Long] = Set.empty): Seq[String] = {
    pages.sortBy(_.id).filterNot(i => existingPageIds.contains(i.id.get)).map {
      page =>
        page.text.flatMap(text => namedParam(text, "Information", "description")).getOrElse("")
    }
  }

  def authors(pages: Seq[Page]): Seq[String] = {
    pages.sortBy(_.id).map {
      page =>
        page.text.flatMap(text => namedParam(text, "Information", "author")).getOrElse("")
    }
  }

  def revisionsByGenerator(source: String,
                           generator: String,
                           generatorPrefix: String,
                           namespaces: Set[Int],
                           props: Set[String],
                           limit: String): Future[Seq[Page]] = {

    commons.page(source)
      .withContext(Map("contestId" -> contest.id.getOrElse(0).toString, "max" -> max.toString))
      .revisionsByGenerator(generator, generatorPrefix,
        Set.empty, Set("content", "timestamp", "user", "comment"), limit = "50", titlePrefix = None
      )
  }
}

object FetchImageText {

  def defaultParam(text: String, templateName: String): Option[String] =
    TemplateParser.parseOne(text, Some(templateName)).flatMap(_.getParamOpt("1"))

  /**
    * Get value of a template parameter
    * @param text page text, that can contain a template with parameter
    * @param templateName template name
    * @param paramName template parameter name
    * @return optional value of template parameter
    */
  def namedParam(text: String, templateName: String, paramName: String): Option[String] =
    TemplateParser.parseOne(text, Some(templateName)).flatMap(_.getParamOpt(paramName))

}