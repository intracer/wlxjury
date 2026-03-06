package services

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.intracer.wmua.{Image, ImageUtil}
import org.scalawiki.MwBot
import org.scalawiki.dto.Namespace
import org.specs2.mutable.Specification
import play.api.Configuration

import java.io.{ByteArrayInputStream, File}
import java.nio.file.Files
import javax.imageio.ImageIO
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class LocalImageCacheServiceIntegrationSpec extends Specification {

  sequential
  skipAllUnless(sys.props.get("integration").contains("true"))

  implicit val system: ActorSystem  = ActorSystem("LocalImageCacheIntegrationSpec")
  implicit val ec: ExecutionContext = system.dispatcher

  // ── helpers ────────────────────────────────────────────────────────────────

  val tempDir: File = Files.createTempDirectory("wlxjury-integration").toFile

  val svc: LocalImageCacheService = {
    val config = Configuration(
      ConfigFactory.parseString(
        s"""wlxjury.thumbs.local-path = "${tempDir.getAbsolutePath}"
           |wlxjury.thumbs.parallelism = 4
           |wlxjury.thumbs.rate-per-second = 5
           |wlxjury.thumbs.max-attempts = 5
           |""".stripMargin
      )
    )
    new LocalImageCacheService(config, system)
  }

  // mirrors LocalImageCacheService.sourceHeight
  private val sourceHeight = (controllers.Global.largeSizeY * 1.5).toInt

  private val imageInfoProps = Set("timestamp", "user", "size", "url", "mime")

  /** Fetch up to `limit` eligible images from a Commons category via scalawiki. */
  def fetchImagesFromCommons(category: String, limit: Int): Seq[Image] = {
    val bot = MwBot.fromHost("commons.wikimedia.org")
    val pages = Await.result(
      bot
        .page(category)
        .imageInfoByGenerator(
          "categorymembers",
          "cm",
          namespaces = Set(Namespace.FILE),
          props      = imageInfoProps,
          titlePrefix = None
        )
        .map(_.toSeq),
      60.seconds
    )
    pages
      .flatMap { page =>
        page.images.headOption.flatMap { ii =>
          for {
            id  <- page.id
            url <- ii.url
            w   <- ii.width
            h   <- ii.height
          } yield Image(id, page.title, Some(url), ii.pageUrl, w, h, mime = ii.mime)
        }
      }
      .filter(img => img.isImage && img.url.isDefined && img.width > 0 && img.height > 0)
      .take(limit)
  }

  /** Download and resize one image. Returns true on success. */
  def processImage(image: Image): Future[Boolean] = {
    val sourcePx = math.min(
      ImageUtil.resizeTo(image.width, image.height, sourceHeight),
      image.width
    )
    val urlOpt = if (sourcePx >= image.width) image.url else svc.wikiThumbUrl(image, sourcePx)
    urlOpt match {
      case None => Future.successful(false)
      case Some(url) =>
        svc.downloadWithRetry(url, attempt = 1).map {
          case Some(bytes) =>
            val sourceImg = ImageIO.read(new ByteArrayInputStream(bytes))
            if (sourceImg != null) {
              svc.saveResized(image, sourceImg, sourcePx)
              true
            } else false
          case None => false
        }
    }
  }

  // ── tests ──────────────────────────────────────────────────────────────────

  "LocalImageCacheService integration" should {

    "download and resize all images from Category:Images from Wiki Loves Earth 2021 in Armenia" in {

      val category = "Category:Images from Wiki Loves Earth 2021 in Armenia"
      val images   = fetchImagesFromCommons(category, limit = 50)

      images must not(beEmpty)

      val startMs = System.currentTimeMillis()

      val results: Seq[Boolean] = Await.result(
        Source(images)
          .throttle(5, 1.second)
          .mapAsyncUnordered(4)(processImage)
          .runWith(Sink.seq),
        60.seconds
      )

      val elapsed   = System.currentTimeMillis() - startMs
      val succeeded = results.count(identity)
      println(f"Integration test: $succeeded/${images.size} images processed in ${elapsed / 1000.0}%.1fs " +
              f"(${succeeded * 1000.0 / elapsed}%.1f img/s)")

      // All images must succeed — this feature exists to eliminate missing images
      succeeded mustEqual images.size

      // Every processed image must have all thumbnail sizes on disk
      images must contain { img: Image => svc.allSizesCached(img) must beTrue }.forall
    }
  }

  step {
    Await.result(system.terminate(), 30.seconds)
    ()
  }
}
