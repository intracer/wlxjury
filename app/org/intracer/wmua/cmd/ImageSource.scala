package org.intracer.wmua.cmd

import db.scalikejdbc.ImageJdbc
import org.intracer.wmua.{Image, ContestJury}
import org.scalawiki.MwBot
import org.scalawiki.dto.{Page, Namespace}
import org.scalawiki.wikitext.TemplateParser
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ImageSource {

}

case class ImageInfoFromCategory(category: String, contest: ContestJury, commons: MwBot) {
  def apply(): Future[Seq[Image]] = {
    val query = commons.page(category)

    val future = query.imageInfoByGenerator("categorymembers", "cm", namespaces = Set(Namespace.FILE),
      props = Set("timestamp", "user", "size", "url"),
      titlePrefix = None)

    future.map {
      pages =>
        pages.flatMap(page => ImageJdbc.fromPage(page, contest))
    }
  }

}

case class ImageTextFromCategory(category: String, contest: ContestJury, monumentIdTemplate: Option[String], commons: MwBot) {

  def apply(): Future[Seq[Image]] = {
    val query = commons.page(category)

    val future = query.revisionsByGenerator("categorymembers", "cm",
      Set.empty, Set("content", "timestamp", "user", "comment"), limit = "50", titlePrefix = None)

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

}




