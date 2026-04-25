package graphql2.views

case class ImageRatedEventView(roundId: String, pageId: String, juryId: String, rate: Int)

case class JurorStatView(user: UserView, rated: Int, selected: Int)

case class RoundStatView(
  roundId:     String,
  totalImages: Int,
  ratedImages: Int,
  jurorStats:  List[JurorStatView]
)
