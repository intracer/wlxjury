package org.intracer.wmua.cmd

import java.net.URLDecoder

import db.scalikejdbc.{ContestJury, ImageJdbc}
import org.intracer.wmua.Image
import org.scalawiki.MwBot
import org.scalawiki.dto.cmd.Action
import org.scalawiki.dto.cmd.query.prop.iiprop.{IiProp, IiPropArgs}
import org.scalawiki.dto.cmd.query.prop.{CategoryInfo, ImageInfo, Prop}
import org.scalawiki.dto.cmd.query.{Query, TitlesParam}
import org.scalawiki.dto.{Namespace, Page}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class FetchImageInfo(source: String, titles: Seq[String] = Seq.empty, contest: ContestJury, commons: MwBot, max: Long = 0L) {

  val imageInfoProps = Set("timestamp", "user", "size", "url")

  def apply(): Future[Seq[Image]] = {
    val imageInfoQuery = if (titles.isEmpty)
      generatorQuery
    else
      titlesQuery

    for (pages <- imageInfoQuery) yield
      pages.flatMap(page => ImageJdbc.fromPage(page, contest))
  }

  def generatorQuery: Future[Seq[Page]] = {
    val (generator, prefix) = if (source.toLowerCase.startsWith("category:")) {
      ("categorymembers", "cm")
    } else if (source.toLowerCase.startsWith("template:")) {
      ("embeddedin", "ei")
    }
    else {
      ("images", "im")
    }

    imageInfoByGenerator(source, generator, prefix)
  }

  def titlesQuery: Future[Seq[Page]] = {

    val trimmed = titles.map(_.trim).filterNot(_.isEmpty)
    val urlDecoded = trimmed
      .map(t => URLDecoder.decode(t, "UTF8"))
      .map(_.replace("https://commons.wikimedia.org/wiki/", ""))

    val iiProps = IiProp(IiPropArgs.byNames(imageInfoProps.toSeq): _*)
    val query = Action(Query(TitlesParam(urlDecoded), Prop(ImageInfo(iiProps))))
    commons.run(query)
  }

  def numberOfImages: Future[Long] = {
    val query = Action(Query(TitlesParam(Seq(source)), Prop(CategoryInfo)))
    commons.run(query).map {
      _.head.categoryInfo.map(_.files).getOrElse(0L)
    }
  }

  def imageInfoByGenerator(source: String, generator: String, generatorPrefix: String): Future[Seq[Page]] = {

    val context = Map("contestId" -> contest.id.getOrElse(0).toString, "max" -> max.toString)

    commons.page(source).withContext(context).imageInfoByGenerator(
      generator, generatorPrefix,
      namespaces = Set(Namespace.FILE),
      props = imageInfoProps,
      titlePrefix = None
    )
  }
}
