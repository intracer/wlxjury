package graphql2.inputs

case class RoundImagesArgs(
  page:     Option[Int],
  pageSize: Option[Int],
  rate:     Option[Int],
  region:   Option[String]
)
