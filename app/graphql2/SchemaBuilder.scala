// app/graphql2/SchemaBuilder.scala
package graphql2

import caliban._
import caliban.schema.{ArgBuilder, GenericSchema, Schema}
import caliban.schema.ArgBuilder.auto._
import graphql2.inputs._
import graphql2.views._
import zio._
import zio.stream.ZStream

object SchemaBuilder extends GenericSchema[GraphQL2Context] {

  // ArgBuilder for RoundImagesArgs (needed for function field in RoundView)
  implicit val roundImagesArgsBuilder: ArgBuilder[RoundImagesArgs] = ArgBuilder.gen[RoundImagesArgs]

  // Explicit schema derivations for view types that contain ZIO[Any, ...] fields
  implicit val userViewSchema: Schema[GraphQL2Context, UserView]                       = gen[GraphQL2Context, UserView]
  implicit val imageViewSchema: Schema[GraphQL2Context, ImageView]                     = gen[GraphQL2Context, ImageView]
  implicit val selectionViewSchema: Schema[GraphQL2Context, SelectionView]             = gen[GraphQL2Context, SelectionView]
  implicit val imageWithRatingViewSchema: Schema[GraphQL2Context, ImageWithRatingView] = gen[GraphQL2Context, ImageWithRatingView]
  implicit val commentViewSchema: Schema[GraphQL2Context, CommentView]                 = gen[GraphQL2Context, CommentView]
  implicit val monumentViewSchema: Schema[GraphQL2Context, MonumentView]               = gen[GraphQL2Context, MonumentView]
  implicit val authPayloadViewSchema: Schema[GraphQL2Context, AuthPayloadView]         = gen[GraphQL2Context, AuthPayloadView]
  implicit val imageRatedEventViewSchema: Schema[GraphQL2Context, ImageRatedEventView] = gen[GraphQL2Context, ImageRatedEventView]
  implicit val jurorStatViewSchema: Schema[GraphQL2Context, JurorStatView]             = gen[GraphQL2Context, JurorStatView]
  implicit val roundStatViewSchema: Schema[GraphQL2Context, RoundStatView]             = gen[GraphQL2Context, RoundStatView]
  // Schema for RoundImagesArgs as input type (needed for functionSchema)
  implicit val roundImagesArgsSchema: Schema[Any, RoundImagesArgs]                    = Schema.gen[Any, RoundImagesArgs]
  // Explicit schema for the function field type used in RoundView.images
  implicit val roundImagesFunctionSchema: Schema[GraphQL2Context, RoundImagesArgs => ZIO[Any, CalibanError, List[ImageWithRatingView]]] =
    Schema.functionSchema(
      roundImagesArgsBuilder,
      roundImagesArgsSchema,
      implicitly[Schema[GraphQL2Context, ZIO[Any, CalibanError, List[ImageWithRatingView]]]]
    )
  implicit val roundViewSchema: Schema[GraphQL2Context, RoundView]                     = gen[GraphQL2Context, RoundView]
  implicit val contestViewSchema: Schema[GraphQL2Context, ContestView]                 = gen[GraphQL2Context, ContestView]

  // Schema[Any, ...] for all input types and arg types used in Queries/Mutations/Subscriptions
  implicit val contestInputSchema: Schema[Any, ContestInput]                  = Schema.gen[Any, ContestInput]
  implicit val roundInputSchema: Schema[Any, RoundInput]                      = Schema.gen[Any, RoundInput]
  implicit val userInputSchema: Schema[Any, UserInput]                        = Schema.gen[Any, UserInput]
  implicit val ratingInputSchema: Schema[Any, RatingInput]                    = Schema.gen[Any, RatingInput]

  // Schema[Any, ArgType] for all arg types used as function parameters in Queries/Mutations/Subscriptions
  implicit val contestArgsSchema: Schema[Any, ContestArgs]                    = Schema.gen[Any, ContestArgs]
  implicit val roundByIdArgsSchema: Schema[Any, RoundByIdArgs]                = Schema.gen[Any, RoundByIdArgs]
  implicit val roundsByContestArgsSchema: Schema[Any, RoundsByContestArgs]    = Schema.gen[Any, RoundsByContestArgs]
  implicit val roundStatArgsSchema: Schema[Any, RoundStatArgs]                = Schema.gen[Any, RoundStatArgs]
  implicit val imagesArgsSchema: Schema[Any, ImagesArgs]                      = Schema.gen[Any, ImagesArgs]
  implicit val imageArgsSchema: Schema[Any, ImageArgs]                        = Schema.gen[Any, ImageArgs]
  implicit val usersArgsSchema: Schema[Any, UsersArgs]                        = Schema.gen[Any, UsersArgs]
  implicit val userByIdArgsSchema: Schema[Any, UserByIdArgs]                  = Schema.gen[Any, UserByIdArgs]
  implicit val commentsArgsSchema: Schema[Any, CommentsArgs]                  = Schema.gen[Any, CommentsArgs]
  implicit val monumentsArgsSchema: Schema[Any, MonumentsArgs]                = Schema.gen[Any, MonumentsArgs]
  implicit val monumentArgsSchema: Schema[Any, MonumentArgs]                  = Schema.gen[Any, MonumentArgs]
  implicit val loginArgsSchema: Schema[Any, LoginArgs]                        = Schema.gen[Any, LoginArgs]
  implicit val createContestArgsSchema: Schema[Any, CreateContestArgs]        = Schema.gen[Any, CreateContestArgs]
  implicit val updateContestArgsSchema: Schema[Any, UpdateContestArgs]        = Schema.gen[Any, UpdateContestArgs]
  implicit val deleteContestArgsSchema: Schema[Any, DeleteContestArgs]        = Schema.gen[Any, DeleteContestArgs]
  implicit val createRoundArgsSchema: Schema[Any, CreateRoundArgs]            = Schema.gen[Any, CreateRoundArgs]
  implicit val updateRoundArgsSchema: Schema[Any, UpdateRoundArgs]            = Schema.gen[Any, UpdateRoundArgs]
  implicit val setActiveRoundArgsSchema: Schema[Any, SetActiveRoundArgs]      = Schema.gen[Any, SetActiveRoundArgs]
  implicit val addJurorsArgsSchema: Schema[Any, AddJurorsArgs]                = Schema.gen[Any, AddJurorsArgs]
  implicit val rateImageArgsSchema: Schema[Any, RateImageArgs]                = Schema.gen[Any, RateImageArgs]
  implicit val rateImageBulkArgsSchema: Schema[Any, RateImageBulkArgs]        = Schema.gen[Any, RateImageBulkArgs]
  implicit val setRoundImagesArgsSchema: Schema[Any, SetRoundImagesArgs]      = Schema.gen[Any, SetRoundImagesArgs]
  implicit val createUserArgsSchema: Schema[Any, CreateUserArgs]              = Schema.gen[Any, CreateUserArgs]
  implicit val updateUserArgsSchema: Schema[Any, UpdateUserArgs]              = Schema.gen[Any, UpdateUserArgs]
  implicit val addCommentArgsSchema: Schema[Any, AddCommentArgs]              = Schema.gen[Any, AddCommentArgs]
  implicit val imageRatedArgsSchema: Schema[Any, ImageRatedArgs]              = Schema.gen[Any, ImageRatedArgs]

  implicit val queriesSchema: Schema[GraphQL2Context, Queries]                      = gen[GraphQL2Context, Queries]
  implicit val mutationsSchema: Schema[GraphQL2Context, Mutations]                   = gen[GraphQL2Context, Mutations]
  implicit val subscriptionsSchema: Schema[GraphQL2Context, Subscriptions]           = gen[GraphQL2Context, Subscriptions]

  private def stub[A]: ZIO[GraphQL2Context, CalibanError, A] =
    ZIO.die(new RuntimeException("not implemented"))

  private def stubStream[A]: ZStream[GraphQL2Context, CalibanError, A] =
    ZStream.die(new RuntimeException("not implemented"))

  val queries: Queries = Queries(
    contests  = stub,
    contest   = _ => stub,
    round     = _ => stub,
    rounds    = _ => stub,
    roundStat = _ => stub,
    images    = _ => stub,
    image     = _ => stub,
    users     = _ => stub,
    user      = _ => stub,
    me        = ZIO.serviceWith[GraphQL2Context](_.currentUser.map(UserView.from)),
    comments  = _ => stub,
    monuments = _ => stub,
    monument  = _ => stub
  )

  val mutations: Mutations = Mutations(
    login            = _ => stub,
    createContest    = _ => stub,
    updateContest    = _ => stub,
    deleteContest    = _ => stub,
    createRound      = _ => stub,
    updateRound      = _ => stub,
    setActiveRound   = _ => stub,
    addJurorsToRound = _ => stub,
    rateImage        = _ => stub,
    rateImageBulk    = _ => stub,
    setRoundImages   = _ => stub,
    createUser       = _ => stub,
    updateUser       = _ => stub,
    addComment       = _ => stub
  )

  val subscriptions: Subscriptions = Subscriptions(
    imageRated    = _ => stubStream,
    roundProgress = _ => stubStream
  )

  val api: GraphQL[GraphQL2Context] =
    graphQL(RootResolver(queries, mutations, subscriptions))

  val interpreter: GraphQLInterpreter[GraphQL2Context, CalibanError] =
    Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(api.interpreter).getOrThrow()
    }
}
