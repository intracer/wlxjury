// app/graphql2/SchemaBuilder.scala
package graphql2

import caliban._
import caliban.schema.{ArgBuilder, GenericSchema, Schema}
import caliban.schema.ArgBuilder.auto._
import db.scalikejdbc.{ContestJuryJdbc, ImageJdbc, Round, RoundUser, SelectionJdbc, User}
import db.scalikejdbc.rewrite.ImageDbNew.{Limit, SelectionQuery}
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
    contests = ZIO.serviceWithZIO[GraphQL2Context] { _ =>
      ZIO.fromFuture(implicit ec =>
        scala.concurrent.Future(ContestJuryJdbc.findAll().map(ContestView.from))
      ).mapError(GraphQL2Context.dbError)
    },

    contest = args => ZIO.serviceWithZIO[GraphQL2Context] { _ =>
      ZIO.fromFuture(implicit ec =>
        scala.concurrent.Future(ContestJuryJdbc.findById(args.id.toLong).map(ContestView.from))
      ).mapError(GraphQL2Context.dbError)
    },
    round = args => ZIO.serviceWithZIO[GraphQL2Context] { _ =>
      ZIO.fromFuture(implicit ec =>
        scala.concurrent.Future(Round.findById(args.id.toLong).map(RoundView.from))
      ).mapError(GraphQL2Context.dbError)
    },

    rounds = args => ZIO.serviceWithZIO[GraphQL2Context] { _ =>
      ZIO.fromFuture(implicit ec =>
        scala.concurrent.Future(Round.findByContest(args.contestId.toLong).map(RoundView.from).toList)
      ).mapError(GraphQL2Context.dbError)
    },

    roundStat = args => ZIO.serviceWithZIO[GraphQL2Context] { _ =>
      ZIO.fromFuture { implicit ec =>
        scala.concurrent.Future {
          val roundId = args.roundId.toLong
          val total   = SelectionQuery(roundId = Some(roundId), grouped = true).count()
          val rated   = SelectionQuery(roundId = Some(roundId), rate = Some(1), grouped = true).count()
          val jurors  = User.findByRoundSelection(roundId)
          val stats   = jurors.map { u =>
            val r = SelectionQuery(roundId = Some(roundId), userId = u.id, grouped = true).count()
            val s = SelectionQuery(roundId = Some(roundId), userId = u.id, rate = Some(1), grouped = true).count()
            JurorStatView(UserView.from(u), r, s)
          }
          RoundStatView(roundId = roundId.toString, totalImages = total, ratedImages = rated, jurorStats = stats.toList)
        }
      }.mapError(GraphQL2Context.dbError)
    },
    images = args => ZIO.serviceWithZIO[GraphQL2Context] { _ =>
      ZIO.fromFuture { implicit ec =>
        scala.concurrent.Future {
          val page     = args.page.getOrElse(1)
          val pageSize = args.pageSize.getOrElse(20)
          val offset   = (page - 1) * pageSize
          SelectionQuery(
            roundId = Some(args.roundId.toLong),
            userId  = args.userId.map(_.toLong),
            rate    = args.rate,
            grouped = args.userId.isEmpty,
            limit   = Some(Limit(Some(pageSize), Some(offset)))
          ).list().map(ImageWithRatingView.from).toList
        }
      }.mapError(GraphQL2Context.dbError)
    },

    image = args => ZIO.serviceWithZIO[GraphQL2Context] { _ =>
      ZIO.fromFuture(implicit ec =>
        scala.concurrent.Future(ImageJdbc.findById(args.pageId.toLong).map(ImageView.from))
      ).mapError(GraphQL2Context.dbError)
    },
    users = args => ZIO.serviceWithZIO[GraphQL2Context] { _ =>
      ZIO.fromFuture { implicit ec =>
        scala.concurrent.Future {
          args.contestId match {
            case Some(id) => User.findAllBy(scalikejdbc.sqls.eq(User.u.contestId, id.toLong)).map(UserView.from).toList
            case None     => User.findAll().map(UserView.from).toList
          }
        }
      }.mapError(GraphQL2Context.dbError)
    },

    user = args => ZIO.serviceWithZIO[GraphQL2Context] { _ =>
      ZIO.fromFuture(implicit ec =>
        scala.concurrent.Future(User.findById(args.id.toLong).map(UserView.from))
      ).mapError(GraphQL2Context.dbError)
    },

    me = ZIO.serviceWith[GraphQL2Context](_.currentUser.map(UserView.from)),
    comments  = _ => stub,
    monuments = _ => stub,
    monument  = _ => stub
  )

  val mutations: Mutations = Mutations(
    login         = _ => stub,
    createContest = args => ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
      ctx.requireRole("root") *>
        ZIO.fromFuture { implicit ec =>
          scala.concurrent.Future(
            ContestJuryJdbc.create(
              None,
              args.input.name,
              args.input.year,
              args.input.country,
              args.input.images,
              None,
              None,
              args.input.monumentIdTemplate,
              args.input.campaign
            )
          ).map(ContestView.from)
        }.mapError(GraphQL2Context.dbError)
    },

    updateContest = args => ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
      ctx.requireRole("root") *>
        ZIO.fromFuture(implicit ec =>
          scala.concurrent.Future(ContestJuryJdbc.findById(args.id.toLong))
        ).mapError(GraphQL2Context.dbError)
          .flatMap {
            case None => ZIO.fail(GraphQL2Context.notFound(s"Contest ${args.id} not found"))
            case Some(existing) =>
              ZIO.fromFuture { implicit ec =>
                val id = args.id.toLong
                scala.concurrent.Future {
                  ContestJuryJdbc.updateById(id).withAttributes(
                    "name"               -> args.input.name,
                    "year"               -> args.input.year,
                    "country"            -> args.input.country,
                    "images"             -> args.input.images,
                    "campaign"           -> args.input.campaign,
                    "monumentIdTemplate" -> args.input.monumentIdTemplate
                  )
                  ContestView.from(existing.copy(
                    name               = args.input.name,
                    year               = args.input.year,
                    country            = args.input.country,
                    images             = args.input.images,
                    campaign           = args.input.campaign,
                    monumentIdTemplate = args.input.monumentIdTemplate
                  ))
                }
              }.mapError(GraphQL2Context.dbError)
          }
    },

    deleteContest = args => ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
      ctx.requireRole("root") *>
        ZIO.fromFuture(implicit ec =>
          scala.concurrent.Future { ContestJuryJdbc.deleteById(args.id.toLong); true }
        ).mapError(GraphQL2Context.dbError)
    },
    createRound = args => ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
      ctx.requireRole("organizer") *>
        ZIO.fromFuture { implicit ec =>
          scala.concurrent.Future(
            Round.create(Round(
              id             = None,
              number         = 0,
              name           = args.input.name,
              contestId      = args.input.contestId.toLong,
              distribution   = args.input.distribution.getOrElse(0),
              minMpx         = args.input.minMpx,
              category       = args.input.category,
              regions        = args.input.regions,
              mediaType      = args.input.mediaType,
              hasCriteria    = args.input.hasCriteria.getOrElse(false),
              previous       = args.input.previousRoundId.map(_.toLong),
              prevSelectedBy = args.input.prevSelectedBy
            ))
          ).map(RoundView.from)
        }.mapError(GraphQL2Context.dbError)
    },

    updateRound = args => ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
      ctx.requireRole("organizer") *>
        ZIO.fromFuture(implicit ec =>
          scala.concurrent.Future(Round.findById(args.id.toLong))
        ).mapError(GraphQL2Context.dbError)
          .flatMap {
            case None => ZIO.fail(GraphQL2Context.notFound(s"Round ${args.id} not found"))
            case Some(existing) =>
              ZIO.fromFuture { implicit ec =>
                val id = args.id.toLong
                scala.concurrent.Future {
                  Round.updateById(id).withAttributes(
                    "name"        -> args.input.name.orElse(existing.name).orNull,
                    "category"    -> args.input.category.orElse(existing.category).orNull,
                    "regions"     -> args.input.regions.orElse(existing.regions).orNull,
                    "mediaType"   -> args.input.mediaType.orElse(existing.mediaType).orNull,
                    "hasCriteria" -> args.input.hasCriteria.getOrElse(existing.hasCriteria),
                    "minMpx"      -> args.input.minMpx.orElse(existing.minMpx).orNull
                  )
                  RoundView.from(Round.findById(id).get)
                }
              }.mapError(GraphQL2Context.dbError)
          }
    },

    setActiveRound = args => ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
      ctx.requireRole("organizer") *>
        ZIO.fromFuture(implicit ec =>
          scala.concurrent.Future(Round.findById(args.id.toLong))
        ).mapError(GraphQL2Context.dbError)
          .flatMap {
            case None => ZIO.fail(GraphQL2Context.notFound(s"Round ${args.id} not found"))
            case Some(round) =>
              ZIO.fromFuture { implicit ec =>
                scala.concurrent.Future {
                  Round.setActive(args.id.toLong, active = true)
                  RoundView.from(round.copy(active = true))
                }
              }.mapError(GraphQL2Context.dbError)
          }
    },

    addJurorsToRound = args => ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
      ctx.requireRole("organizer") *>
        ZIO.fromFuture(implicit ec =>
          scala.concurrent.Future(Round.findById(args.roundId.toLong))
        ).mapError(GraphQL2Context.dbError)
          .flatMap {
            case None => ZIO.fail(GraphQL2Context.notFound(s"Round ${args.roundId} not found"))
            case Some(round) =>
              ZIO.fromFuture { implicit ec =>
                scala.concurrent.Future {
                  val roundId = args.roundId.toLong
                  round.addUsers(args.userIds.map(uid => RoundUser(roundId, uid.toLong, "jury", active = true)))
                  RoundView.from(Round.findById(roundId).get)
                }
              }.mapError(GraphQL2Context.dbError)
          }
    },
    rateImage = args => ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
      ctx.requireAuth *> ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
        ZIO.fromFuture { implicit ec =>
          scala.concurrent.Future {
            val user    = ctx.currentUser.get
            val roundId = args.roundId.toLong
            val pageId  = args.pageId.toLong
            SelectionJdbc.findBy(pageId, user.getId, roundId) match {
              case Some(_) =>
                SelectionJdbc.rate(pageId, user.getId, roundId, args.rate)
                SelectionJdbc.findBy(pageId, user.getId, roundId).map(SelectionView.from).get
              case None =>
                SelectionView.from(SelectionJdbc.create(pageId, args.rate, user.getId, roundId))
            }
          }
        }.mapError(GraphQL2Context.dbError)
      }
    },

    rateImageBulk = args => ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
      ctx.requireAuth *> ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
        ZIO.fromFuture { implicit ec =>
          scala.concurrent.Future {
            val user    = ctx.currentUser.get
            val roundId = args.roundId.toLong
            args.ratings.map { r =>
              val pageId = r.pageId.toLong
              SelectionJdbc.findBy(pageId, user.getId, roundId) match {
                case Some(_) =>
                  SelectionJdbc.rate(pageId, user.getId, roundId, r.rate)
                  SelectionJdbc.findBy(pageId, user.getId, roundId).map(SelectionView.from).get
                case None =>
                  SelectionView.from(SelectionJdbc.create(pageId, r.rate, user.getId, roundId))
              }
            }
          }
        }.mapError(GraphQL2Context.dbError)
      }
    },

    setRoundImages = args => ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
      ctx.requireRole("organizer") *>
        ZIO.fromFuture(implicit ec =>
          scala.concurrent.Future {
            ContestJuryJdbc.setImagesSource(args.roundId.toLong, Some(args.category))
            true
          }
        ).mapError(GraphQL2Context.dbError)
    },
    createUser = args => ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
      ctx.requireRole("admin") *>
        ZIO.fromFuture { implicit ec =>
          scala.concurrent.Future(
            UserView.from(User.create(User(
              fullname    = args.input.fullname,
              email       = args.input.email,
              roles       = args.input.roles.toSet,
              contestId   = args.input.contestId.map(_.toLong),
              lang        = args.input.lang,
              wikiAccount = args.input.wikiAccount
            )))
          )
        }.mapError(GraphQL2Context.dbError)
    },

    updateUser = args => ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
      ctx.requireRole("admin") *>
        ZIO.fromFuture(implicit ec =>
          scala.concurrent.Future(User.findById(args.id.toLong))
        ).mapError(GraphQL2Context.dbError)
          .flatMap {
            case None => ZIO.fail(GraphQL2Context.notFound(s"User ${args.id} not found"))
            case Some(existing) =>
              ZIO.fromFuture { implicit ec =>
                val id = args.id.toLong
                scala.concurrent.Future {
                  User.updateById(id).withAttributes(
                    "fullname"    -> args.input.fullname,
                    "email"       -> args.input.email,
                    "roles"       -> args.input.roles.mkString(","),
                    "contestId"   -> args.input.contestId.map(_.toLong).orElse(existing.contestId).orNull,
                    "lang"        -> args.input.lang.orElse(existing.lang).orNull,
                    "wikiAccount" -> args.input.wikiAccount.orElse(existing.wikiAccount).orNull
                  )
                  UserView.from(User.findById(id).get)
                }
              }.mapError(GraphQL2Context.dbError)
          }
    },
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
