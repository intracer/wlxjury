package graphql2.views

import caliban.CalibanError
import db.scalikejdbc.rewrite.ImageDbNew.{Limit, SelectionQuery}
import db.scalikejdbc.{Round, User}
import graphql2.inputs.RoundImagesArgs
import zio._

case class RoundView(
  id:           String,
  number:       Int,
  name:         Option[String],
  contestId:    String,
  active:       Boolean,
  distribution: Int,
  minMpx:       Option[Int],
  category:     Option[String],
  regions:      Option[String],
  mediaType:    Option[String],
  hasCriteria:  Boolean,
  jurors:       ZIO[Any, CalibanError, List[UserView]],
  images:       RoundImagesArgs => ZIO[Any, CalibanError, List[ImageWithRatingView]]
)

object RoundView {
  def from(r: Round): RoundView = RoundView(
    id           = r.id.fold("")(_.toString),
    number       = r.number.toInt,
    name         = r.name,
    contestId    = r.contestId.toString,
    active       = r.active,
    distribution = r.distribution,
    minMpx       = r.minMpx,
    category     = r.category,
    regions      = r.regions,
    mediaType    = r.mediaType,
    hasCriteria  = r.hasCriteria,
    jurors       = ZIO.fromFuture(implicit ec =>
      scala.concurrent.Future(User.findByRoundSelection(r.getId).map(UserView.from).toList)
    ).mapError(e => CalibanError.ExecutionError(e.getMessage): CalibanError),
    images       = args => {
      val page     = args.page.getOrElse(1)
      val pageSize = args.pageSize.getOrElse(20)
      val offset   = (page - 1) * pageSize
      ZIO.fromFuture(implicit ec =>
        scala.concurrent.Future(
          SelectionQuery(
            roundId = r.id,
            rate    = args.rate,
            grouped = true,
            limit   = Some(Limit(Some(pageSize), Some(offset)))
          ).list().map(ImageWithRatingView.from).toList
        )
      ).mapError(e => CalibanError.ExecutionError(e.getMessage): CalibanError)
    }
  )
}
