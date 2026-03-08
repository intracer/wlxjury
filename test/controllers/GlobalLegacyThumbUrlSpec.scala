package controllers

import org.intracer.wmua.Image
import org.specs2.mutable.Specification

class GlobalLegacyThumbUrlSpec extends Specification {

  // Helpers to build test images
  private def jpegImage(url: String, width: Int = 800, height: Int = 600): Image =
    Image(pageId = 1L, title = "File:Corallus.jpg", url = Some(url), width = width, height = height)

  private def pdfImage(url: String): Image =
    Image(pageId = 2L, title = "File:Report.pdf", url = Some(url), width = 800, height = 600)

  private def tifImage(url: String): Image =
    Image(pageId = 3L, title = "File:Painting.tif", url = Some(url), width = 800, height = 600)

  private def tiffImage(url: String): Image =
    Image(pageId = 4L, title = "File:Painting.tiff", url = Some(url), width = 800, height = 600)

  private val httpsUrl  = "https://upload.wikimedia.org/wikipedia/commons/9/9e/Corallus.jpg"
  private val protoUrl  = "//upload.wikimedia.org/wikipedia/commons/9/9e/Corallus.jpg"
  private val pdfHttps  = "https://upload.wikimedia.org/wikipedia/commons/a/ab/Report.pdf"
  private val tifHttps  = "https://upload.wikimedia.org/wikipedia/commons/b/bc/Painting.tif"
  private val tiffHttps = "https://upload.wikimedia.org/wikipedia/commons/c/cd/Painting.tiff"

  // Long title: > 165 UTF-8 bytes → thumbnail.jpg replaces the filename
  private val longTitle = "File:" + "А" * 100  // 100 Cyrillic chars = 200 UTF-8 bytes
  private def longTitleImage(url: String): Image =
    Image(pageId = 5L, title = longTitle,
      url = Some(url), width = 800, height = 600)

  "legacyThumbUrl" should {

    "replace https:// upload.wikimedia.org with remote host (https)" in {
      val result = Global.legacyThumbUrl(jpegImage(httpsUrl), px = 250, host = "upload.wikimedia.org")
      result must startWith("https://upload.wikimedia.org/wikipedia/commons/thumb/")
      result must contain("/250px-Corallus.jpg")
    }

    "replace https:// upload.wikimedia.org with localhost (http)" in {
      val result = Global.legacyThumbUrl(jpegImage(httpsUrl), px = 324, host = "localhost:9000")
      result must startWith("http://localhost:9000/wikipedia/commons/thumb/")
      result must contain("/324px-Corallus.jpg")
    }

    "replace protocol-relative //upload.wikimedia.org with remote host (https)" in {
      val result = Global.legacyThumbUrl(jpegImage(protoUrl), px = 250, host = "upload.wikimedia.org")
      result must startWith("https://upload.wikimedia.org/wikipedia/commons/thumb/")
      result must contain("/250px-Corallus.jpg")
    }

    "replace protocol-relative //upload.wikimedia.org with localhost (http)" in {
      val result = Global.legacyThumbUrl(jpegImage(protoUrl), px = 324, host = "localhost:9000")
      result must startWith("http://localhost:9000/wikipedia/commons/thumb/")
      result must contain("/324px-Corallus.jpg")
    }

    "include the hash path segments from the original URL" in {
      val result = Global.legacyThumbUrl(jpegImage(httpsUrl), px = 250, host = "localhost:9000")
      result must contain("/9/9e/Corallus.jpg/")
    }

    "produce the correct full URL structure" in {
      val result = Global.legacyThumbUrl(jpegImage(httpsUrl), px = 250, host = "localhost:9000")
      result mustEqual "http://localhost:9000/wikipedia/commons/thumb/9/9e/Corallus.jpg/250px-Corallus.jpg"
    }

    "add page1- prefix for PDF files" in {
      val result = Global.legacyThumbUrl(pdfImage(pdfHttps), px = 250, host = "upload.wikimedia.org")
      result must contain("/250px-page1-Report.pdf.jpg")
        .or(contain("/page1-250px-Report.pdf"))
      result must contain("page1-")
    }

    "add lossy-page1- prefix for TIF files" in {
      val result = Global.legacyThumbUrl(tifImage(tifHttps), px = 250, host = "upload.wikimedia.org")
      result must contain("lossy-page1-")
    }

    "add lossy-page1- prefix for TIFF files" in {
      val result = Global.legacyThumbUrl(tiffImage(tiffHttps), px = 250, host = "upload.wikimedia.org")
      result must contain("lossy-page1-")
    }

    "append .jpg suffix for PDF thumbnails" in {
      val result = Global.legacyThumbUrl(pdfImage(pdfHttps), px = 250, host = "upload.wikimedia.org")
      result must endWith(".jpg")
    }

    "append .jpg suffix for TIF thumbnails" in {
      val result = Global.legacyThumbUrl(tifImage(tifHttps), px = 250, host = "upload.wikimedia.org")
      result must endWith(".jpg")
    }

    "use thumbnail.jpg for images with title longer than 165 UTF-8 bytes" in {
      val url = "https://upload.wikimedia.org/wikipedia/commons/d/de/" + ("A" * 50) + ".jpg"
      val result = Global.legacyThumbUrl(longTitleImage(url), px = 250, host = "upload.wikimedia.org")
      result must contain("thumbnail.jpg")
      result must not contain (longTitle.drop(5)) // filename part should not appear
    }

    "use the actual filename for images with title within 165 UTF-8 bytes" in {
      val result = Global.legacyThumbUrl(jpegImage(httpsUrl), px = 250, host = "upload.wikimedia.org")
      result must contain("Corallus.jpg")
      result must not contain "thumbnail.jpg"
    }

    "use https scheme for a production host" in {
      val result = Global.legacyThumbUrl(jpegImage(httpsUrl), px = 500, host = "my.jury.org")
      result must startWith("https://my.jury.org/")
    }

    "use http scheme for 127.0.0.1 host" in {
      val result = Global.legacyThumbUrl(jpegImage(httpsUrl), px = 500, host = "127.0.0.1:9000")
      result must startWith("http://127.0.0.1:9000/")
    }
  }
}
