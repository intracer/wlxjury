package services

import controllers.Global
import db.scalikejdbc.ImageJdbc
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.pattern.after
import org.apache.pekko.stream.scaladsl._
import org.intracer.wmua.{Image, ImageUtil}
import play.api.libs.json.{Json, Writes}
import play.api.{Configuration, Logging}

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.{ByteArrayInputStream, File}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

case class CacheProgress(
    done: Int,
    total: Int,
    errors: Int,
    running: Boolean,
    startedAtMs: Long  = 0L,
    elapsedMs: Long    = 0L,
    ratePerSec: Double = 0.0,
    etaSeconds: Long   = 0L
)

object CacheProgress {
  implicit val writes: Writes[CacheProgress] = Json.writes[CacheProgress]
}

@Singleton
class LocalImageCacheService @Inject() (
    config: Configuration,
    actorSystem: ActorSystem
)(implicit ec: ExecutionContext)
    extends Logging {

  implicit private val system: ActorSystem = actorSystem

  private val localPath   = config.get[String]("wlxjury.thumbs.local-path")
  private val parallelism = config.getOptional[Int]("wlxjury.thumbs.parallelism").getOrElse(8)
  private val ratePerSec  = config.getOptional[Int]("wlxjury.thumbs.rate-per-second").getOrElse(5)
  private val maxAttempts = config.getOptional[Int]("wlxjury.thumbs.max-attempts").getOrElse(10)

  // All thumbnail heights used by the jury UI (1x, 1.5x and 2x for each size slot)
  private val targetHeights: Seq[Int] = Seq(
    Global.thumbSizeY,                     // 120  – thumbs bar 1x
    (Global.thumbSizeY * 1.5).toInt,       // 180  – thumbs bar 1.5x
    Global.thumbSizeY * 2,                 // 240  – thumbs bar 2x
    Global.gallerySizeY,                   // 250  – gallery 1x
    (Global.gallerySizeY * 1.5).toInt,     // 375  – gallery 1.5x
    Global.gallerySizeY * 2,               // 500  – gallery 2x
    Global.largeSizeY,                     // 1100 – large view 1x
    (Global.largeSizeY * 1.5).toInt        // 1650 – large view 1.5x
  ).distinct.sorted

  // The source image downloaded from wikimedia covers all target heights
  private val sourceHeight = (Global.largeSizeY * 1.5).toInt // 1650

  private val userAgent = headers.RawHeader("User-Agent", "WLXJury/1.0 (Wikimedia jury tool)")

  private val progressMap = new ConcurrentHashMap[Long, CacheProgress]()

  def progress(contestId: Long): CacheProgress =
    Option(progressMap.get(contestId)).getOrElse(CacheProgress(0, 0, 0, running = false))

  def startDownload(contestId: Long): Unit = {
    if (progress(contestId).running) return

    val images = ImageJdbc
      .findByContestId(contestId)
      .filter(img => img.isImage && img.url.isDefined && img.width > 0 && img.height > 0)
    val total = images.size
    val done  = new AtomicInteger(0)
    val errs  = new AtomicInteger(0)
    val startMs = System.currentTimeMillis()

    def mkProgress(d: Int, running: Boolean): CacheProgress = {
      val elapsed = System.currentTimeMillis() - startMs
      val rate    = if (elapsed > 0) d * 1000.0 / elapsed else 0.0
      val eta     = if (rate > 0) ((total - d) / rate).toLong else 0L
      CacheProgress(d, total, errs.get, running,
        startedAtMs = startMs, elapsedMs = elapsed, ratePerSec = rate, etaSeconds = eta)
    }

    progressMap.put(contestId, mkProgress(0, running = true))

    Source(images)
      .filterNot(allSizesCached)
      .throttle(ratePerSec, 1.second)
      .mapAsyncUnordered(parallelism) { image =>
        downloadAndResize(image)
          .recover { case ex =>
            logger.warn(s"Failed to cache ${image.title}: ${ex.getMessage}")
            errs.incrementAndGet()
          }
          .map { _ =>
            val d = done.incrementAndGet()
            progressMap.put(contestId, mkProgress(d, running = true))
          }
      }
      .runWith(Sink.ignore)
      .onComplete { _ =>
        progressMap.put(contestId, mkProgress(done.get, running = false))
      }
  }

  private[services] def allSizesCached(image: Image): Boolean =
    targetHeights.forall { h =>
      val px = ImageUtil.resizeTo(image.width, image.height, h)
      px >= image.width || localFile(image, px).exists()
    }

  // Mirrors the URL construction in Global.legacyThumbUlr, always using upload.wikimedia.org
  private[services] def wikiThumbUrl(image: Image, px: Int): Option[String] =
    image.url.filter(_.contains("//upload.wikimedia.org/wikipedia/commons/")).map { url =>
      val lower     = image.title.toLowerCase
      val isPdf     = lower.endsWith(".pdf")
      val isTif     = lower.endsWith(".tif") || lower.endsWith(".tiff")
      val lastSlash = url.lastIndexOf("/")
      val utf8Size  = image.title.getBytes("UTF-8").length
      val thumbStr  = if (utf8Size > 165) "thumbnail.jpg" else url.substring(lastSlash + 1)
      url.replace(
        "//upload.wikimedia.org/wikipedia/commons/",
        "//upload.wikimedia.org/wikipedia/commons/thumb/"
      ) + "/" +
        (if (isPdf) "page1-" else if (isTif) "lossy-page1-" else "") +
        px + "px-" + thumbStr +
        (if (isPdf || isTif) ".jpg" else "")
    }

  // Local path mirrors the URL path, so Apache can serve it under the same URL structure
  private[services] def localFile(image: Image, px: Int): File = {
    val path = wikiThumbUrl(image, px)
      .getOrElse("")
      .replaceFirst("https?://upload\\.wikimedia\\.org", "")
    new File(localPath + path)
  }

  private def downloadAndResize(image: Image): Future[Unit] = {
    val sourcePx = math.min(
      ImageUtil.resizeTo(image.width, image.height, sourceHeight),
      image.width
    )
    // Use original URL if source size equals full image width, otherwise use a thumb
    val urlOpt = if (sourcePx >= image.width) image.url else wikiThumbUrl(image, sourcePx)

    urlOpt match {
      case None => Future.successful(())
      case Some(url) =>
        downloadWithRetry(url, attempt = 1).map {
          case Some(bytes) =>
            val sourceImg = ImageIO.read(new ByteArrayInputStream(bytes))
            if (sourceImg != null) saveResized(image, sourceImg, sourcePx)
          case None => ()
        }
    }
  }

  /** Downloads a URL, retrying on 429 with exponential backoff + jitter.
    * Respects the Retry-After header if present. Gives up after maxAttempts.
    */
  private[services] def downloadWithRetry(url: String, attempt: Int): Future[Option[Array[Byte]]] =
    Http()
      .singleRequest(HttpRequest(uri = url, headers = List(userAgent)))
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
          entity.toStrict(60.seconds).map(e => Some(e.data.toArray))

        case HttpResponse(StatusCodes.TooManyRequests, respHeaders, entity, _) =>
          entity.discardBytes()
          if (attempt >= maxAttempts) {
            logger.warn(s"Giving up on $url after $attempt attempts (429)")
            Future.successful(None)
          } else {
            val delay = retryDelay(respHeaders, attempt)
            logger.info(s"429 on attempt $attempt for $url, retrying in ${delay.toSeconds}s")
            after(delay)(downloadWithRetry(url, attempt + 1))
          }

        case resp =>
          resp.entity.discardBytes()
          logger.warn(s"HTTP ${resp.status} for $url")
          Future.successful(None)
      }

  /** Delay before next retry: Retry-After header value if present, otherwise
    * exponential backoff (2^attempt seconds) capped at 5 minutes, with jitter.
    */
  private[services] def retryDelay(respHeaders: Seq[HttpHeader], attempt: Int): FiniteDuration = {
    val retryAfterSeconds = respHeaders
      .find(_.name.toLowerCase == "retry-after")
      .flatMap(h => scala.util.Try(h.value.trim.toInt).toOption)

    val baseSeconds = retryAfterSeconds.getOrElse {
      math.min(math.pow(2, attempt).toInt, 300) // cap at 5 min
    }
    // Add up to 30% jitter to avoid thundering herd when many images retry simultaneously
    val jitter = Random.nextInt(math.max(1, baseSeconds / 3))
    (baseSeconds + jitter).seconds
  }

  private[services] def saveResized(image: Image, sourceImg: BufferedImage, sourcePx: Int): Unit =
    targetHeights.foreach { h =>
      val px = ImageUtil.resizeTo(image.width, image.height, h)
      if (px > 0 && px < image.width && px <= sourcePx) {
        val file = localFile(image, px)
        if (!file.exists()) {
          file.getParentFile.mkdirs()
          val resized = scale(sourceImg, px)
          ImageIO.write(resized, "JPEG", file)
        }
      }
    }

  private[services] def scale(src: BufferedImage, targetWidth: Int): BufferedImage = {
    val targetHeight = (src.getHeight.toDouble * targetWidth / src.getWidth).toInt
    val out = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
    val g   = out.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.drawImage(src, 0, 0, targetWidth, targetHeight, null)
    g.dispose()
    out
  }
}
