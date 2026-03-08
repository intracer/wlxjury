package controllers

import db.scalikejdbc.ImageJdbc
import play.api.mvc._
import services.LocalImageCacheService

import java.net.URLDecoder
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class ImageProxyController @Inject() (
    cc: ControllerComponents,
    cache: LocalImageCacheService
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  private val logger = play.api.Logger(getClass)

  def serve(path: String): Action[AnyContent] = Action.async { _ =>
    val segments = path.split("/").toSeq
    // Expect at least: wikipedia/commons/thumb/X/XX/filename/Npx-filename
    val parsed = for {
      rawFilename <- if (segments.length >= 2) Some(segments(segments.length - 2)) else None
      filename    = URLDecoder.decode(rawFilename, "UTF-8")
      lastSeg    <- segments.lastOption
      px         <- Try(lastSeg.takeWhile(_ != 'p').toInt).toOption
      if px > 0
    } yield (filename, px)

    parsed match {
      case None =>
        Future.successful(NotFound)

      case Some((filename, px)) =>
        val imageOpt = Try(ImageJdbc.findByFilename(filename)).getOrElse(None)
        imageOpt match {
          case None =>
            // Not in DB: redirect to Wikimedia; browser follows transparently for <img>
            Future.successful(
              Redirect(s"https://upload.wikimedia.org/$path", SEE_OTHER)
            )

          case Some(image) =>
            cache.fileIfCached(image, px) match {
              case Some(file) =>
                Future.successful(Ok.sendFile(file, inline = true)
                  .as("image/jpeg")
                  .withHeaders("Cache-Control" -> "public, max-age=2592000"))

              case None =>
                cache.fetchAndCacheAll(image, px)
                  .map { bytes =>
                    Ok(bytes).as("image/jpeg")
                      .withHeaders("Cache-Control" -> "public, max-age=2592000")
                  }
                  .recover { case ex =>
                    logger.warn(s"Failed to fetch $path: ${ex.getMessage}")
                    Redirect(s"https://upload.wikimedia.org/$path", SEE_OTHER)
                  }
            }
        }
    }
  }
}
