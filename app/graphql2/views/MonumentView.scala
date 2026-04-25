package graphql2.views

import org.scalawiki.wlx.dto.Monument

case class MonumentView(
  id:          String,
  name:        String,
  description: Option[String],
  year:        Option[String],
  city:        Option[String],
  place:       Option[String],
  lat:         Option[String],
  lon:         Option[String],
  typ:         Option[String],
  subType:     Option[String],
  photo:       Option[String],
  contestId:   Option[String]
)

object MonumentView {
  def from(m: Monument): MonumentView = MonumentView(
    id          = m.id,
    name        = m.name,
    description = m.description,
    year        = m.year,
    city        = m.city,
    place       = m.place,
    lat         = m.lat,
    lon         = m.lon,
    typ         = m.typ,
    subType     = m.subType,
    photo       = m.photo,
    contestId   = m.contest.map(_.toString)
  )
}
