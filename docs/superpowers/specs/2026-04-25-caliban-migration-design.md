# Caliban GraphQL Migration Design

**Date:** 2026-04-25  
**Branch:** graphql  
**Goal:** Parallel Caliban-based GraphQL implementation alongside existing Sangria implementation, for side-by-side comparison before deciding which library to keep.

---

## Overview

The existing Sangria implementation lives in `app/graphql/` and serves `POST /graphql`. The Caliban port will live entirely in `app/graphql2/` and serve `POST /graphql2`, registered alongside Sangria in `ApiServer`. The two implementations are independent — no shared code, no cross-package imports. After comparison the losing implementation will be deleted.

---

## Dependencies

Add to `build.sbt`:

```scala
val CalibanVersion = "2.9.1"
val ZioVersion     = "2.1.14"

"com.github.ghostdogpr" %% "caliban"                     % CalibanVersion,
"com.github.ghostdogpr" %% "caliban-pekko-http"          % CalibanVersion,
"dev.zio"               %% "zio"                         % ZioVersion,
"dev.zio"               %% "zio-streams"                 % ZioVersion,
"dev.zio"               %% "zio-interop-reactivestreams" % "2.0.2",
```

`caliban-pekko-http` transitively brings in circe for JSON handling. No changes to Sangria or existing Pekko HTTP dependencies.

---

## ZIO Integration Strategy

**Shallow ZIO**: resolver business logic stays Future-based. All resolver fields wrap their `Future` calls with `ZIO.fromFuture(ec => ...)`. No rewriting of service/DAO layer. Auth guards use `ZIO.serviceWithZIO[GraphQL2Context](_.requireRole(...))`.

**Context injection**: `GraphQL2Context` is the ZIO environment type `R` for all resolver effects (typed `ZIO[GraphQL2Context, Throwable, A]`). The schema and interpreter are built once at startup. Per request the route provides context via `ZLayer.succeed(ctx)`:

```scala
interpreter.execute(request).provideLayer(ZLayer.succeed(ctx))
```

---

## Package Structure

### Sources: `app/graphql2/`

```
app/graphql2/
  views/
    ContestView.scala           # case class ContestView + companion.from(ContestJury)
    RoundView.scala             # case class RoundView + companion.from(Round)
    UserView.scala              # case class UserView (no password) + companion.from(User)
    ImageView.scala             # case class ImageView + companion.from(Image)
    SelectionView.scala         # case class SelectionView + companion.from(Selection)
    ImageWithRatingView.scala   # case class ImageWithRatingView + companion.from(ImageWithRating)
    CommentView.scala           # case class CommentView + companion.from(Comment)
    MonumentView.scala          # case class MonumentView + companion.from(Monument)
    AuthPayloadView.scala       # case class AuthPayloadView(token: String, user: UserView)
    SubscriptionViews.scala     # ImageRatedEventView, RoundStatView, JurorStatView
  inputs/
    Inputs.scala                # All input + args case classes (see Inputs section)
  GraphQL2Context.scala         # Auth context as ZIO service type
  Queries.scala                 # case class Queries(fields as ZIO[GraphQL2Context, ...])
  Mutations.scala               # case class Mutations(fields as ZIO[GraphQL2Context, ...])
  Subscriptions.scala           # case class Subscriptions(fields as ZStream[GraphQL2Context, ...])
  SchemaBuilder.scala           # graphQL(RootResolver(...)) + cached interpreter
  GraphQL2Route.scala           # Pekko HTTP handler at /graphql2
```

### Tests: `test/graphql2/`

```
test/graphql2/
  ContestResolverSpec2.scala
  RoundResolverSpec2.scala
  ImageResolverSpec2.scala
  UserResolverSpec2.scala
  CommentResolverSpec2.scala
  MonumentResolverSpec2.scala
  AuthSpec2.scala
  SubscriptionSpec2.scala
  SchemaValidationSpec2.scala
  GraphQL2RouteSpec2.scala
```

---

## Views

Each view is a plain Scala case class. Caliban derives the GraphQL output schema automatically via the `Schema` typeclass (no `ObjectType`, `Field`, or `resolve` boilerplate). All Long IDs are exposed as `String` to match the existing schema.

```scala
// views/ContestView.scala
case class ContestView(
  id: String, name: String, year: Int, country: String,
  images: Option[String], currentRound: Option[String],
  monumentIdTemplate: Option[String], campaign: Option[String]
)
object ContestView {
  def from(c: ContestJury): ContestView = ContestView(
    id = c.id.fold("")(_.toString), name = c.name, year = c.year,
    country = c.country, images = c.images,
    currentRound = c.currentRound.map(_.toString),
    monumentIdTemplate = c.monumentIdTemplate, campaign = c.campaign
  )
}

// views/UserView.scala — password intentionally excluded
case class UserView(
  id: String, fullname: String, email: String, roles: List[String],
  contestId: Option[String], lang: Option[String],
  wikiAccount: Option[String], active: Option[Boolean]
)

// views/SubscriptionViews.scala
case class ImageRatedEventView(roundId: String, pageId: String, juryId: String, rate: Int)
case class JurorStatView(user: UserView, rated: Int, selected: Int)
case class RoundStatView(roundId: String, totalImages: Int, ratedImages: Int, jurorStats: List[JurorStatView])
```

Nested relation fields on views that require DB calls (e.g. `rounds` on `ContestView`, `jurors`/`images` on `RoundView`) are typed as `ZIO[GraphQL2Context, Throwable, List[...]]` directly on the view case class. Caliban resolves these lazily when the field is requested.

---

## Inputs

All input types and resolver argument types live in `app/graphql2/inputs/Inputs.scala`. Caliban derives `ArgBuilder` (input schema) automatically from case classes — no `InputObjectType`, `InputField`, or casting helpers needed.

**Input types** (mutation payloads):
```scala
case class ContestInput(name: String, year: Int, country: String,
  images: Option[String], monumentIdTemplate: Option[String], campaign: Option[String])

case class RoundInput(contestId: String, name: Option[String], distribution: Option[Int],
  minMpx: Option[Int], category: Option[String], regions: Option[String],
  mediaType: Option[String], hasCriteria: Option[Boolean],
  previousRoundId: Option[String], prevSelectedBy: Option[Int])

case class UserInput(fullname: String, email: String, roles: List[String],
  contestId: Option[String], lang: Option[String], wikiAccount: Option[String])

case class RatingInput(pageId: String, rate: Int)
```

**Args types** (query/mutation arguments — replace Sangria `Argument(...)` declarations):
```scala
case class ContestArgs(id: String)
case class RoundByIdArgs(id: String)
case class RoundsByContestArgs(contestId: String)
case class RoundStatArgs(roundId: String)
case class ImagesArgs(roundId: String, userId: Option[String],
  page: Option[Int], pageSize: Option[Int], rate: Option[Int], region: Option[String])
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
```

---

## Context & Error Handling

```scala
// GraphQL2Context.scala
case class GraphQL2Context(currentUser: Option[User], eventBus: SubscriptionEventBus) {
  def requireAuth: IO[AuthenticationError, User] =
    ZIO.fromOption(currentUser).orElseFail(AuthenticationError("Not authenticated"))
  def requireRole(role: String): IO[Throwable, User] =
    requireAuth.flatMap { u =>
      if (u.hasRole(role) || u.hasRole("root")) ZIO.succeed(u)
      else ZIO.fail(AuthorizationError(s"Requires role: $role"))
    }
}

// Same four error types as Sangria: AuthenticationError, AuthorizationError, NotFoundError, BadInputError
// Mapped to Caliban ExecutionError extensions with the same code strings:
// UNAUTHENTICATED, FORBIDDEN, NOT_FOUND, BAD_INPUT, INTERNAL_ERROR
```

Error extension mapping is implemented via Caliban's `CalibanError.ExecutionError` wrapping — errors thrown in `ZIO.fromFuture` or returned as `ZIO.fail(...)` are caught and converted to `GraphQLResponse` errors with `extensions: { code: "..." }` via a custom error handler registered on the interpreter.

---

## Queries, Mutations & Subscriptions

### Queries (`Queries.scala`)

```scala
case class Queries(
  contests:   ZIO[GraphQL2Context, Throwable, List[ContestView]],
  contest:    ContestArgs           => ZIO[GraphQL2Context, Throwable, Option[ContestView]],
  round:      RoundByIdArgs         => ZIO[GraphQL2Context, Throwable, Option[RoundView]],
  rounds:     RoundsByContestArgs   => ZIO[GraphQL2Context, Throwable, List[RoundView]],
  roundStat:  RoundStatArgs         => ZIO[GraphQL2Context, Throwable, RoundStatView],
  images:     ImagesArgs            => ZIO[GraphQL2Context, Throwable, List[ImageWithRatingView]],
  image:      ImageArgs             => ZIO[GraphQL2Context, Throwable, Option[ImageView]],
  users:      UsersArgs             => ZIO[GraphQL2Context, Throwable, List[UserView]],
  user:       UserByIdArgs          => ZIO[GraphQL2Context, Throwable, Option[UserView]],
  me:         ZIO[GraphQL2Context, Nothing, Option[UserView]],
  comments:   CommentsArgs          => ZIO[GraphQL2Context, Throwable, List[CommentView]],
  monuments:  MonumentsArgs         => ZIO[GraphQL2Context, Throwable, List[MonumentView]],
  monument:   MonumentArgs          => ZIO[GraphQL2Context, Throwable, Option[MonumentView]]
)
```

### Mutations (`Mutations.scala`)

```scala
case class Mutations(
  login:            LoginArgs            => ZIO[GraphQL2Context, Throwable, AuthPayloadView],
  createContest:    CreateContestArgs    => ZIO[GraphQL2Context, Throwable, ContestView],
  updateContest:    UpdateContestArgs    => ZIO[GraphQL2Context, Throwable, ContestView],
  deleteContest:    DeleteContestArgs    => ZIO[GraphQL2Context, Throwable, Boolean],
  createRound:      CreateRoundArgs      => ZIO[GraphQL2Context, Throwable, RoundView],
  updateRound:      UpdateRoundArgs      => ZIO[GraphQL2Context, Throwable, RoundView],
  setActiveRound:   SetActiveRoundArgs   => ZIO[GraphQL2Context, Throwable, RoundView],
  addJurorsToRound: AddJurorsArgs        => ZIO[GraphQL2Context, Throwable, RoundView],
  rateImage:        RateImageArgs        => ZIO[GraphQL2Context, Throwable, SelectionView],
  rateImageBulk:    RateImageBulkArgs    => ZIO[GraphQL2Context, Throwable, List[SelectionView]],
  setRoundImages:   SetRoundImagesArgs   => ZIO[GraphQL2Context, Throwable, Boolean],
  createUser:       CreateUserArgs       => ZIO[GraphQL2Context, Throwable, UserView],
  updateUser:       UpdateUserArgs       => ZIO[GraphQL2Context, Throwable, UserView],
  addComment:       AddCommentArgs       => ZIO[GraphQL2Context, Throwable, CommentView]
)
```

### Subscriptions (`Subscriptions.scala`)

```scala
case class Subscriptions(
  imageRated:    ImageRatedArgs => ZStream[GraphQL2Context, Throwable, ImageRatedEventView],
  roundProgress: RoundStatArgs  => ZStream[GraphQL2Context, Throwable, ImageRatedEventView]
)
```

Subscription fields convert the existing Pekko Streams `Source` from `SubscriptionEventBus` to `ZStream` via `zio-interop-reactivestreams`:
```scala
imageRated = args => ZStream.fromZIO(ZIO.service[GraphQL2Context]).flatMap { ctx =>
  val source    = ctx.eventBus.imageRatedSource.filter(_.roundId == args.roundId.toLong)
  val publisher = source.toMat(Sink.asPublisher(fanout = false))(Keep.right).run()
  ZStream.fromPublisher(publisher).map(e => ImageRatedEventView(...))
}
```

---

## SchemaBuilder

```scala
// SchemaBuilder.scala
object SchemaBuilder {
  val queries: Queries = Queries(
    contests = ZIO.serviceWithZIO[GraphQL2Context](_ =>
      ZIO.fromFuture(_ => Future(ContestJuryJdbc.findAll().map(ContestView.from)))),
    // ... all other fields
  )

  val mutations: Mutations = Mutations(
    login = args => ZIO.serviceWithZIO[GraphQL2Context] { _ =>
      ZIO.fromFuture { _ => Future {
        val user = User.findByEmail(args.email).headOption
          .filter(_.password.exists(_ == User.sha1(args.password)))
          .getOrElse(throw AuthenticationError("Invalid email or password"))
        val token = JwtJson.encode(...)
        AuthPayloadView(token, UserView.from(user))
      }}
    },
    // ... all other fields, auth mutations use ZIO.serviceWithZIO[GraphQL2Context](_.requireRole(...))
  )

  val subscriptions: Subscriptions = Subscriptions(...)

  val api: GraphQL[GraphQL2Context] = graphQL(RootResolver(queries, mutations, subscriptions))

  // Built once at startup; safe because schema structure is static
  val interpreter: GraphQLInterpreter[GraphQL2Context, CalibanError] =
    Unsafe.unsafe { implicit u => Runtime.default.unsafe.run(api.interpreter).getOrThrow() }
}
```

---

## Route

`GraphQL2Route` mounts at `/graphql2`. Auth extraction is identical to `GraphQLRoute` (JWT Bearer + `X-Authenticated-User` proxy header, same `resolveUser` logic). Uses `caliban-pekko-http`'s `PekkoHttpAdapter` for JSON request/response handling:

```scala
path("graphql2") {
  optionalHeaderValueByName("Authorization") { authHeader =>
    optionalHeaderValueByName("X-Authenticated-User") { proxyUser =>
      val ctx   = GraphQL2Context(resolveUser(authHeader, proxyUser), eventBus)
      val layer = ZLayer.succeed(ctx)
      PekkoHttpAdapter.makeHttpService(SchemaBuilder.interpreter)(
        requestToLayerR = _ => layer
      )
    }
  }
}
```

Registered in `ApiServer`:
```scala
private val route = concat(api.routes, api.swaggerRoute, graphqlRoute.route, graphql2Route.route)
```

---

## Tests

All specs in `test/graphql2/` extend `Specification with BeforeAll` and use `SharedTestDb` for database setup — same pattern as Sangria specs.

**Direct interpreter execution** (9 of 10 specs):
```scala
def run[A](zio: ZIO[GraphQL2Context, CalibanError, A], ctx: GraphQL2Context = defaultCtx): A =
  Unsafe.unsafe { implicit u =>
    Runtime.default.unsafe.run(zio.provideLayer(ZLayer.succeed(ctx))).getOrThrow()
  }

def execute(query: String, ctx: GraphQL2Context = defaultCtx): GraphQLResponse[CalibanError] =
  run(SchemaBuilder.interpreter.execute(query), ctx)
```

Tests assert on `result.data` fields and `result.errors.head.extensions("code")` for auth errors.

**`GraphQL2RouteSpec2`** (HTTP path): uses `Specs2RouteTest` + Pekko HTTP testkit, same as `GraphQLRouteSpec`, to verify auth headers and HTTP-level behaviour.

**`SchemaValidationSpec2`**: calls `SchemaBuilder.api.render` (Caliban's SDL renderer) to assert the schema is well-formed and matches expected type names.

---

## What This Replaces (Sangria Boilerplate Eliminated)

| Sangria | Caliban |
|---------|---------|
| `ObjectType(name, () => fields(...))` per type | Case class, auto-derived |
| `Field("x", StringType, resolve = ...)` per field | Case class field, auto-derived |
| `InputObjectType(name, List(InputField(...)))` | Case class, auto-derived |
| `Argument("x", IDType)` per field | Args case class field |
| `InputTypes.str(m, "key")` casting helpers | Direct case class field access |
| `ExceptionHandler` with `scalarNode` casting | ZIO error handler with pattern match |
| `PekkoSubscriptionStream` adapter (~50 lines) | `ZStream.fromPublisher` one-liner |
| `Field.subs(...)` for subscriptions | ZStream-valued case class field |
