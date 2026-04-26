package graphql2.inputs

// ── Mutation payload inputs ────────────────────────────────────────────────

case class ContestInput(
  name:               String,
  year:               Int,
  country:            String,
  images:             Option[String],
  monumentIdTemplate: Option[String],
  campaign:           Option[String]
)

case class RoundInput(
  contestId:       String,
  name:            Option[String],
  distribution:    Option[Int],
  minMpx:          Option[Int],
  category:        Option[String],
  regions:         Option[String],
  mediaType:       Option[String],
  hasCriteria:     Option[Boolean],
  previousRoundId: Option[String],
  prevSelectedBy:  Option[Int]
)

case class UserInput(
  fullname:    String,
  email:       String,
  roles:       List[String],
  contestId:   Option[String],
  lang:        Option[String],
  wikiAccount: Option[String]
)

case class RatingInput(pageId: String, rate: Int)

// ── Query / mutation argument types ────────────────────────────────────────

case class ContestArgs(id: String)
case class RoundByIdArgs(id: String)
case class RoundsByContestArgs(contestId: String)
case class RoundStatArgs(roundId: String)
case class RoundImagesArgs(
  page:     Option[Int],
  pageSize: Option[Int],
  rate:     Option[Int],
  region:   Option[String]
)
case class ImagesArgs(
  roundId:  String,
  userId:   Option[String],
  page:     Option[Int],
  pageSize: Option[Int],
  rate:     Option[Int],
  region:   Option[String]
)
case class ImageArgs(pageId: String)
case class UsersArgs(contestId: Option[String])
case class UserByIdArgs(id: String)
case class CommentsArgs(roundId: String, imagePageId: Option[String])
case class MonumentsArgs(contestId: String)
case class MonumentArgs(id: String)
case class LoginArgs(email: String, password: String)
case class CreateContestArgs(input: ContestInput)
case class UpdateContestArgs(id: String, input: ContestInput)
case class DeleteContestArgs(id: String)
case class CreateRoundArgs(input: RoundInput)
case class UpdateRoundArgs(id: String, input: RoundInput)
case class SetActiveRoundArgs(id: String)
case class AddJurorsArgs(roundId: String, userIds: List[String])
case class RateImageArgs(roundId: String, pageId: String, rate: Int, criteriaId: Option[Int])
case class RateImageBulkArgs(roundId: String, ratings: List[RatingInput])
case class SetRoundImagesArgs(roundId: String, category: String)
case class CreateUserArgs(input: UserInput)
case class UpdateUserArgs(id: String, input: UserInput)
case class AddCommentArgs(roundId: String, imagePageId: String, body: String)
case class ImageRatedArgs(roundId: String)
