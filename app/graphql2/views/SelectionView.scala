package graphql2.views

import org.intracer.wmua.Selection

case class SelectionView(
  id:         Option[String],
  pageId:     String,
  juryId:     String,
  roundId:    String,
  rate:       Int,
  criteriaId: Option[Int],
  monumentId: Option[String],
  createdAt:  Option[String]
)

object SelectionView {
  def from(s: Selection): SelectionView = SelectionView(
    id         = s.id.map(_.toString),
    pageId     = s.pageId.toString,
    juryId     = s.juryId.toString,
    roundId    = s.roundId.toString,
    rate       = s.rate,
    criteriaId = s.criteriaId,
    monumentId = s.monumentId,
    createdAt  = s.createdAt.map(_.toString)
  )
}
