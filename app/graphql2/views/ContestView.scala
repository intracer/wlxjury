package graphql2.views

import caliban.CalibanError
import db.scalikejdbc.Round
import org.intracer.wmua.ContestJury
import zio._

case class ContestView(
  id:                  String,
  name:                String,
  year:                Int,
  country:             String,
  images:              Option[String],
  currentRound:        Option[String],
  monumentIdTemplate:  Option[String],
  campaign:            Option[String],
  rounds:              ZIO[Any, CalibanError, List[RoundView]]
)

object ContestView {
  def from(c: ContestJury): ContestView = ContestView(
    id                 = c.id.fold("")(_.toString),
    name               = c.name,
    year               = c.year,
    country            = c.country,
    images             = c.images,
    currentRound       = c.currentRound.map(_.toString),
    monumentIdTemplate = c.monumentIdTemplate,
    campaign           = c.campaign,
    rounds             = ZIO.fromFuture(implicit ec =>
      scala.concurrent.Future(Round.findByContest(c.getId).map(RoundView.from).toList)
    ).mapError(e => CalibanError.ExecutionError(e.getMessage): CalibanError)
  )
}
