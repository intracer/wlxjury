package graphql2.views

import org.intracer.wmua.Image

case class ImageView(
  pageId:     String,
  title:      String,
  url:        Option[String],
  pageUrl:    Option[String],
  width:      Int,
  height:     Int,
  monumentId: Option[String],
  author:     Option[String],
  mime:       Option[String],
  mpx:        Option[Double],
  isVideo:    Boolean
)

object ImageView {
  def from(i: Image): ImageView = ImageView(
    pageId     = i.pageId.toString,
    title      = i.title,
    url        = i.url,
    pageUrl    = i.pageUrl,
    width      = i.width,
    height     = i.height,
    monumentId = i.monumentId,
    author     = i.author,
    mime       = i.mime,
    mpx        = Some(i.mpx),
    isVideo    = i.isVideo
  )
}
