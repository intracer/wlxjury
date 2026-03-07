package services

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.intracer.wmua.Image
import org.specs2.matcher.MatchResult
import org.scalawiki.MwBot
import org.scalawiki.dto.Namespace
import org.specs2.mutable.Specification
import play.api.Configuration

import java.io.File
import java.nio.file.Files
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class LocalImageCacheServiceIntegrationSpec extends Specification {

  sequential
  skipAllUnless(sys.props.get("integration").contains("true"))

  implicit lazy val system: ActorSystem  = ActorSystem("LocalImageCacheIntegrationSpec")
  implicit lazy val ec: ExecutionContext = system.dispatcher

  // ── helpers ────────────────────────────────────────────────────────────────

  lazy val tempDir: File = {
    val d = Files.createTempDirectory("wlxjury-integration").toFile
    d.deleteOnExit()
    d
  }

  lazy val svc: LocalImageCacheService = {
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

  private val imageInfoProps = Set("size", "url", "mime")

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

  // ── tests ──────────────────────────────────────────────────────────────────

  "LocalImageCacheService integration" should {

    "download and resize all images from Category:Images from Wiki Loves Earth 2021 in Armenia" in {
      val category = "Category:Images from Wiki Loves Earth 2021 in Armenia"
      val images   = fetchImagesFromCommons(category, limit = 50)

      images must not(beEmpty)

      val startMs = System.currentTimeMillis()
      Await.result(svc.runDownload(contestId = 1L, images), 60.seconds)
      val elapsed = System.currentTimeMillis() - startMs

      val p = svc.progress(1L)
      println(f"Integration test: ${p.done}/${images.size} images processed in ${elapsed / 1000.0}%.1fs (${p.ratePerSec}%.1f img/s)")

      p.done mustEqual images.size

      images.foldLeft(ok: MatchResult[Any]) { (acc, img) =>
        acc and (svc.allSizesCached(img) must beTrue.updateMessage(m => s"${img.title}: $m"))
      }
    }
  }

  step {
    Await.result(Http().shutdownAllConnectionPools(), 10.seconds)
    Await.result(system.terminate(), 30.seconds)
    ()
  }
}
