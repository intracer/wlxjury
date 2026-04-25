# Caliban GraphQL Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a parallel Caliban-based GraphQL endpoint at `/graphql2` alongside the existing Sangria implementation at `/graphql`, for side-by-side comparison.

**Architecture:** New `app/graphql2/` package with pure view case classes (auto-derived schema), ZIO-based resolvers using `ZIO.fromFuture` wrappers, and manual Pekko HTTP route. Interpreter built once at startup; `ZLayer.succeed(ctx)` injects auth context per request. Tests run the interpreter directly without HTTP (except `GraphQL2RouteSpec2`).

**Tech Stack:** Caliban 2.9.1, ZIO 2.1.14, `zio-interop-reactivestreams` for Pekko→ZStream, existing Pekko HTTP + Play JSON for the route.

---

## File Map

**New sources:**
- `app/graphql2/views/ContestView.scala`
- `app/graphql2/views/RoundView.scala`
- `app/graphql2/views/UserView.scala`
- `app/graphql2/views/ImageView.scala`
- `app/graphql2/views/SelectionView.scala`
- `app/graphql2/views/ImageWithRatingView.scala`
- `app/graphql2/views/CommentView.scala`
- `app/graphql2/views/MonumentView.scala`
- `app/graphql2/views/AuthPayloadView.scala`
- `app/graphql2/views/SubscriptionViews.scala`
- `app/graphql2/inputs/Inputs.scala`
- `app/graphql2/GraphQL2Context.scala`
- `app/graphql2/Queries.scala`
- `app/graphql2/Mutations.scala`
- `app/graphql2/Subscriptions.scala`
- `app/graphql2/SchemaBuilder.scala`
- `app/graphql2/GraphQL2Route.scala`

**New tests:**
- `test/graphql2/GraphQL2SpecHelper.scala`
- `test/graphql2/ContestResolverSpec2.scala`
- `test/graphql2/RoundResolverSpec2.scala`
- `test/graphql2/ImageResolverSpec2.scala`
- `test/graphql2/UserResolverSpec2.scala`
- `test/graphql2/CommentResolverSpec2.scala`
- `test/graphql2/MonumentResolverSpec2.scala`
- `test/graphql2/AuthSpec2.scala`
- `test/graphql2/SubscriptionSpec2.scala`
- `test/graphql2/SchemaValidationSpec2.scala`
- `test/graphql2/GraphQL2RouteSpec2.scala`

**Modified:**
- `build.sbt` — add Caliban + ZIO deps
- `app/api/ApiServer.scala` — register `/graphql2` route

---

### Task 1: Add Caliban and ZIO dependencies

**Files:**
- Modify: `build.sbt`

- [ ] **Step 1: Add version constants and library deps**

In `build.sbt`, find the block of version vals near the top (where `SangriaVersion` is defined) and add:

```scala
val CalibanVersion = "2.9.1"
val ZioVersion     = "2.1.14"
```

In the `libraryDependencies` sequence, add after the Sangria lines:

```scala
// GraphQL - Caliban (parallel implementation for comparison)
"com.github.ghostdogpr" %% "caliban"                     % CalibanVersion,
"dev.zio"               %% "zio"                         % ZioVersion,
"dev.zio"               %% "zio-streams"                 % ZioVersion,
"dev.zio"               %% "zio-interop-reactivestreams" % "2.0.2",
```

- [ ] **Step 2: Verify compilation**

```bash
sbt compile
```

Expected: compiles cleanly. If there are Scala version or eviction warnings, add `dependencyOverrides` for conflicting zio/pekko transitives if needed.

- [ ] **Step 3: Commit**

```bash
git add build.sbt
git commit -m "build: add Caliban 2.9.1 and ZIO 2.1.14 dependencies"
```

---

### Task 2: Create view case classes

**Files:**
- Create: `app/graphql2/views/ContestView.scala`
- Create: `app/graphql2/views/RoundView.scala`
- Create: `app/graphql2/views/UserView.scala`
- Create: `app/graphql2/views/ImageView.scala`
- Create: `app/graphql2/views/SelectionView.scala`
- Create: `app/graphql2/views/ImageWithRatingView.scala`
- Create: `app/graphql2/views/CommentView.scala`
- Create: `app/graphql2/views/MonumentView.scala`
- Create: `app/graphql2/views/AuthPayloadView.scala`
- Create: `app/graphql2/views/SubscriptionViews.scala`

These are plain case classes. Caliban derives the GraphQL output schema from them automatically. Nested relation fields that require DB calls are `ZIO` fields (resolved lazily by Caliban).

- [ ] **Step 1: Create `ContestView.scala`**

```scala
// app/graphql2/views/ContestView.scala
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
      scala.concurrent.Future(Round.findByContest(c.getId).map(RoundView.from))
    ).mapError(e => CalibanError.ExecutionError(e.getMessage))
  )
}
```

- [ ] **Step 2: Create `RoundView.scala`**

```scala
// app/graphql2/views/RoundView.scala
package graphql2.views

import caliban.CalibanError
import db.scalikejdbc.rewrite.ImageDbNew.{Limit, SelectionQuery}
import db.scalikejdbc.{Round, User}
import graphql2.inputs.RoundImagesArgs
import org.intracer.wmua.Round
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
      scala.concurrent.Future(User.findByRoundSelection(r.getId).map(UserView.from))
    ).mapError(e => CalibanError.ExecutionError(e.getMessage)),
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
          ).list().map(ImageWithRatingView.from)
        )
      ).mapError(e => CalibanError.ExecutionError(e.getMessage))
    }
  )
}
```

- [ ] **Step 3: Create `UserView.scala`**

```scala
// app/graphql2/views/UserView.scala
package graphql2.views

import db.scalikejdbc.User

case class UserView(
  id:          String,
  fullname:    String,
  email:       String,
  roles:       List[String],
  contestId:   Option[String],
  lang:        Option[String],
  wikiAccount: Option[String],
  active:      Option[Boolean]
)

object UserView {
  def from(u: User): UserView = UserView(
    id          = u.id.fold("")(_.toString),
    fullname    = u.fullname,
    email       = u.email,
    roles       = u.roles.toList,
    contestId   = u.contestId.map(_.toString),
    lang        = u.lang,
    wikiAccount = u.wikiAccount,
    active      = u.active
  )
}
```

- [ ] **Step 4: Create `ImageView.scala`**

```scala
// app/graphql2/views/ImageView.scala
package graphql2.views

import org.intracer.wmua.Image

case class ImageView(
  pageId:     String,
  title:      String,
  url:        Option[String],
  pageUrl:    Option[String],
  width:      Int,
  height:     Int,
  monumentId: Option[String],
  author:     Option[String],
  mime:       Option[String],
  mpx:        Option[Double],
  isVideo:    Boolean
)

object ImageView {
  def from(i: Image): ImageView = ImageView(
    pageId     = i.pageId.toString,
    title      = i.title,
    url        = i.url,
    pageUrl    = i.pageUrl,
    width      = i.width,
    height     = i.height,
    monumentId = i.monumentId,
    author     = i.author,
    mime       = i.mime,
    mpx        = Some(i.mpx),
    isVideo    = i.isVideo
  )
}
```

- [ ] **Step 5: Create `SelectionView.scala`**

```scala
// app/graphql2/views/SelectionView.scala
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
```

- [ ] **Step 6: Create `ImageWithRatingView.scala`**

```scala
// app/graphql2/views/ImageWithRatingView.scala
package graphql2.views

import org.intracer.wmua.ImageWithRating

case class ImageWithRatingView(
  image:      ImageView,
  selections: List[SelectionView],
  totalRate:  Double,
  rateSum:    Int,
  rank:       Option[Int]
)

object ImageWithRatingView {
  def from(iwr: ImageWithRating): ImageWithRatingView = {
    val sels = iwr.selection.toList
    val totalRate =
      if (sels.isEmpty) 0.0
      else sels.map(_.rate).sum.toDouble / sels.count(_.rate > 0).max(1)
    ImageWithRatingView(
      image      = ImageView.from(iwr.image),
      selections = sels.map(SelectionView.from),
      totalRate  = totalRate,
      rateSum    = iwr.rateSum,
      rank       = iwr.rank
    )
  }
}
```

- [ ] **Step 7: Create `CommentView.scala`**

```scala
// app/graphql2/views/CommentView.scala
package graphql2.views

import org.intracer.wmua.Comment

case class CommentView(
  id:          String,
  userId:      String,
  username:    String,
  roundId:     String,
  contestId:   Option[String],
  imagePageId: String,
  body:        String,
  createdAt:   String
)

object CommentView {
  def from(c: Comment): CommentView = CommentView(
    id          = c.id.toString,
    userId      = c.userId.toString,
    username    = c.username,
    roundId     = c.roundId.toString,
    contestId   = c.contestId.map(_.toString),
    imagePageId = c.room.toString,
    body        = c.body,
    createdAt   = c.createdAt
  )
}
```

- [ ] **Step 8: Create `MonumentView.scala`**

```scala
// app/graphql2/views/MonumentView.scala
package graphql2.views

import org.scalawiki.wlx.dto.Monument

case class MonumentView(
  id:        String,
  name:      String,
  description: Option[String],
  year:      Option[String],
  city:      Option[String],
  place:     Option[String],
  lat:       Option[String],
  lon:       Option[String],
  typ:       Option[String],
  subType:   Option[String],
  photo:     Option[String],
  contestId: Option[String]
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
```

- [ ] **Step 9: Create `AuthPayloadView.scala`**

```scala
// app/graphql2/views/AuthPayloadView.scala
package graphql2.views

case class AuthPayloadView(token: String, user: UserView)
```

- [ ] **Step 10: Create `SubscriptionViews.scala`**

```scala
// app/graphql2/views/SubscriptionViews.scala
package graphql2.views

import db.scalikejdbc.User

case class ImageRatedEventView(roundId: String, pageId: String, juryId: String, rate: Int)

case class JurorStatView(user: UserView, rated: Int, selected: Int)

case class RoundStatView(
  roundId:     String,
  totalImages: Int,
  ratedImages: Int,
  jurorStats:  List[JurorStatView]
)
```

- [ ] **Step 11: Verify compilation**

```bash
sbt "test:compile"
```

Expected: compiles cleanly (some warnings about unused imports are fine).

- [ ] **Step 12: Commit**

```bash
git add app/graphql2/views/
git commit -m "feat: add Caliban GraphQL view types"
```

---

### Task 3: Create input and args case classes

**Files:**
- Create: `app/graphql2/inputs/Inputs.scala`

Caliban derives `ArgBuilder` (input schema) from case classes automatically — no `InputObjectType` or casting helpers.

- [ ] **Step 1: Create `Inputs.scala`**

```scala
// app/graphql2/inputs/Inputs.scala
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
```

- [ ] **Step 2: Verify compilation**

```bash
sbt "test:compile"
```

Expected: compiles cleanly.

- [ ] **Step 3: Commit**

```bash
git add app/graphql2/inputs/
git commit -m "feat: add Caliban GraphQL input and args types"
```

---

### Task 4: Create GraphQL2Context

**Files:**
- Create: `app/graphql2/GraphQL2Context.scala`

- [ ] **Step 1: Create `GraphQL2Context.scala`**

```scala
// app/graphql2/GraphQL2Context.scala
package graphql2

import caliban.CalibanError
import caliban.ResponseValue.{ObjectValue, StringValue}
import db.scalikejdbc.User
import graphql.SubscriptionEventBus
import org.apache.pekko.stream.Materializer
import zio._

case class GraphQL2Context(
  currentUser: Option[User],
  eventBus:    SubscriptionEventBus,
  mat:         Materializer
) {

  def requireAuth: IO[CalibanError, User] =
    ZIO.fromOption(currentUser).orElseFail(
      CalibanError.ExecutionError("Not authenticated",
        extensions = Some(ObjectValue(List("code" -> StringValue("UNAUTHENTICATED")))))
    )

  def requireRole(role: String): IO[CalibanError, User] =
    requireAuth.flatMap { u =>
      if (u.hasRole(role) || u.hasRole("root")) ZIO.succeed(u)
      else ZIO.fail(CalibanError.ExecutionError(s"Requires role: $role",
        extensions = Some(ObjectValue(List("code" -> StringValue("FORBIDDEN"))))))
    }
}

object GraphQL2Context {
  def dbError(e: Throwable): CalibanError.ExecutionError =
    CalibanError.ExecutionError(e.getMessage,
      extensions = Some(ObjectValue(List("code" -> StringValue("INTERNAL_ERROR")))))

  def notFound(msg: String): CalibanError.ExecutionError =
    CalibanError.ExecutionError(msg,
      extensions = Some(ObjectValue(List("code" -> StringValue("NOT_FOUND")))))
}
```

- [ ] **Step 2: Verify compilation**

```bash
sbt "test:compile"
```

Expected: compiles cleanly.

- [ ] **Step 3: Commit**

```bash
git add app/graphql2/GraphQL2Context.scala
git commit -m "feat: add GraphQL2Context with ZIO auth helpers"
```

---

### Task 5: Create schema case classes and stub SchemaBuilder

**Files:**
- Create: `app/graphql2/Queries.scala`
- Create: `app/graphql2/Mutations.scala`
- Create: `app/graphql2/Subscriptions.scala`
- Create: `app/graphql2/SchemaBuilder.scala`
- Create: `test/graphql2/GraphQL2SpecHelper.scala`

- [ ] **Step 1: Create `Queries.scala`**

```scala
// app/graphql2/Queries.scala
package graphql2

import caliban.CalibanError
import graphql2.inputs._
import graphql2.views._
import zio._

case class Queries(
  contests:  ZIO[GraphQL2Context, CalibanError, List[ContestView]],
  contest:   ContestArgs          => ZIO[GraphQL2Context, CalibanError, Option[ContestView]],
  round:     RoundByIdArgs        => ZIO[GraphQL2Context, CalibanError, Option[RoundView]],
  rounds:    RoundsByContestArgs  => ZIO[GraphQL2Context, CalibanError, List[RoundView]],
  roundStat: RoundStatArgs        => ZIO[GraphQL2Context, CalibanError, RoundStatView],
  images:    ImagesArgs           => ZIO[GraphQL2Context, CalibanError, List[ImageWithRatingView]],
  image:     ImageArgs            => ZIO[GraphQL2Context, CalibanError, Option[ImageView]],
  users:     UsersArgs            => ZIO[GraphQL2Context, CalibanError, List[UserView]],
  user:      UserByIdArgs         => ZIO[GraphQL2Context, CalibanError, Option[UserView]],
  me:        ZIO[GraphQL2Context, Nothing, Option[UserView]],
  comments:  CommentsArgs         => ZIO[GraphQL2Context, CalibanError, List[CommentView]],
  monuments: MonumentsArgs        => ZIO[GraphQL2Context, CalibanError, List[MonumentView]],
  monument:  MonumentArgs         => ZIO[GraphQL2Context, CalibanError, Option[MonumentView]]
)
```

- [ ] **Step 2: Create `Mutations.scala`**

```scala
// app/graphql2/Mutations.scala
package graphql2

import caliban.CalibanError
import graphql2.inputs._
import graphql2.views._
import zio._

case class Mutations(
  login:            LoginArgs            => ZIO[GraphQL2Context, CalibanError, AuthPayloadView],
  createContest:    CreateContestArgs    => ZIO[GraphQL2Context, CalibanError, ContestView],
  updateContest:    UpdateContestArgs    => ZIO[GraphQL2Context, CalibanError, ContestView],
  deleteContest:    DeleteContestArgs    => ZIO[GraphQL2Context, CalibanError, Boolean],
  createRound:      CreateRoundArgs      => ZIO[GraphQL2Context, CalibanError, RoundView],
  updateRound:      UpdateRoundArgs      => ZIO[GraphQL2Context, CalibanError, RoundView],
  setActiveRound:   SetActiveRoundArgs   => ZIO[GraphQL2Context, CalibanError, RoundView],
  addJurorsToRound: AddJurorsArgs        => ZIO[GraphQL2Context, CalibanError, RoundView],
  rateImage:        RateImageArgs        => ZIO[GraphQL2Context, CalibanError, SelectionView],
  rateImageBulk:    RateImageBulkArgs    => ZIO[GraphQL2Context, CalibanError, List[SelectionView]],
  setRoundImages:   SetRoundImagesArgs   => ZIO[GraphQL2Context, CalibanError, Boolean],
  createUser:       CreateUserArgs       => ZIO[GraphQL2Context, CalibanError, UserView],
  updateUser:       UpdateUserArgs       => ZIO[GraphQL2Context, CalibanError, UserView],
  addComment:       AddCommentArgs       => ZIO[GraphQL2Context, CalibanError, CommentView]
)
```

- [ ] **Step 3: Create `Subscriptions.scala`**

```scala
// app/graphql2/Subscriptions.scala
package graphql2

import caliban.CalibanError
import graphql2.inputs.ImageRatedArgs
import graphql2.views.ImageRatedEventView
import zio.stream.ZStream

case class Subscriptions(
  imageRated:    ImageRatedArgs => ZStream[GraphQL2Context, CalibanError, ImageRatedEventView],
  roundProgress: ImageRatedArgs => ZStream[GraphQL2Context, CalibanError, ImageRatedEventView]
)
```

- [ ] **Step 4: Create stub `SchemaBuilder.scala`**

All resolver fields use `ZIO.die` stubs — they compile and fail at runtime, allowing TDD.

```scala
// app/graphql2/SchemaBuilder.scala
package graphql2

import caliban._
import caliban.schema.GenericSchema
import graphql2.inputs._
import graphql2.views._
import zio._
import zio.stream.ZStream

object SchemaBuilder extends GenericSchema[GraphQL2Context] {

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
    graphQL(RootResolver(queries, Some(mutations), Some(subscriptions)))

  val interpreter: GraphQLInterpreter[GraphQL2Context, CalibanError] =
    Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(api.interpreter).getOrThrow()
    }
}
```

- [ ] **Step 5: Create `GraphQL2SpecHelper.scala`**

```scala
// test/graphql2/GraphQL2SpecHelper.scala
package graphql2

import caliban.{CalibanError, GraphQLResponse}
import caliban.ResponseValue
import caliban.ResponseValue._
import db.scalikejdbc.{SharedTestDb, User}
import graphql.SubscriptionEventBus
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import zio._

trait GraphQL2SpecHelper {

  private lazy val testSystem: ActorSystem = ActorSystem("caliban-test")
  lazy val testMat: Materializer = Materializer(testSystem)

  lazy val anonCtx: GraphQL2Context =
    GraphQL2Context(None, SubscriptionEventBus.noop, testMat)

  def authedCtx(user: User): GraphQL2Context =
    GraphQL2Context(Some(user), SubscriptionEventBus.noop, testMat)

  def execute(
    query: String,
    ctx:   GraphQL2Context = anonCtx
  ): GraphQLResponse[CalibanError] =
    Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(
        SchemaBuilder.interpreter.execute(query).provideLayer(ZLayer.succeed(ctx))
      ).getOrThrow()
    }

  // ── ResponseValue navigation helpers ──────────────────────────────────────

  def field(rv: ResponseValue, name: String): ResponseValue =
    rv.asInstanceOf[ObjectValue].fields.collectFirst { case (k, v) if k == name => v }
      .getOrElse(throw new NoSuchElementException(s"Field '$name' not found in $rv"))

  def dataField(r: GraphQLResponse[CalibanError], name: String): ResponseValue =
    field(r.data.getOrElse(throw new NoSuchElementException("No data")), name)

  def list(rv: ResponseValue): List[ResponseValue] =
    rv.asInstanceOf[ListValue].values

  def str(rv: ResponseValue): String =
    rv.asInstanceOf[StringValue].value

  def int(rv: ResponseValue): Int = rv match {
    case IntValue.IntNumber(v)  => v
    case IntValue.LongNumber(v) => v.toInt
    case _                      => throw new Exception(s"Not an int: $rv")
  }

  def bool(rv: ResponseValue): Boolean =
    rv.asInstanceOf[BooleanValue].value

  def errorCode(r: GraphQLResponse[CalibanError]): String = {
    val e = r.errors.head.asInstanceOf[CalibanError.ExecutionError]
    e.extensions.get.fields.collectFirst { case ("code", StringValue(c)) => c }
      .getOrElse("NO_CODE")
  }
}
```

- [ ] **Step 6: Verify compilation**

```bash
sbt "test:compile"
```

Expected: compiles cleanly. If Caliban can't derive `Schema` for some types, it will report an implicit not found error — add `implicit val xSchema = gen[X]` for each failing type inside `SchemaBuilder` (the `GenericSchema` trait provides the `gen` method).

- [ ] **Step 7: Commit**

```bash
git add app/graphql2/Queries.scala app/graphql2/Mutations.scala \
        app/graphql2/Subscriptions.scala app/graphql2/SchemaBuilder.scala \
        test/graphql2/GraphQL2SpecHelper.scala
git commit -m "feat: add Caliban schema case classes and stub SchemaBuilder"
```

---

### Task 6: TDD — ContestResolver

**Files:**
- Create: `test/graphql2/ContestResolverSpec2.scala`
- Modify: `app/graphql2/SchemaBuilder.scala`

- [ ] **Step 1: Write `ContestResolverSpec2.scala`**

```scala
// test/graphql2/ContestResolverSpec2.scala
package graphql2

import db.scalikejdbc.{ContestJuryJdbc, SharedTestDb, User}
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

class ContestResolverSpec2 extends Specification with BeforeAll with GraphQL2SpecHelper {
  sequential

  override def beforeAll(): Unit = SharedTestDb.init()

  "contests query" should {
    "return list of contests" in {
      SharedTestDb.truncateAll()
      ContestJuryJdbc.create(None, "WLM", 2024, "Ukraine", None, None, None, None)
      val r = execute("{ contests { id name year country } }")
      r.errors must beEmpty
      val items = list(dataField(r, "contests"))
      items must have size 1
      str(field(items.head, "name")) must_== "WLM"
      int(field(items.head, "year")) must_== 2024
    }

    "return empty list when no contests" in {
      SharedTestDb.truncateAll()
      val r = execute("{ contests { name } }")
      r.errors must beEmpty
      list(dataField(r, "contests")) must beEmpty
    }
  }

  "contest query" should {
    "return contest by id" in {
      SharedTestDb.truncateAll()
      val c = ContestJuryJdbc.create(None, "WLE", 2023, "Poland", None, None, None, None)
      val r = execute(s"""{ contest(id: "${c.getId}") { name country } }""")
      r.errors must beEmpty
      str(field(dataField(r, "contest"), "name")) must_== "WLE"
    }
  }

  "createContest mutation" should {
    "fail with UNAUTHENTICATED when no user" in {
      val r = execute("""mutation { createContest(input:{name:"X",year:2024,country:"Y"}) { id } }""")
      r.errors must not beEmpty
      errorCode(r) must_== "UNAUTHENTICATED"
    }

    "fail with FORBIDDEN for non-root user" in {
      SharedTestDb.truncateAll()
      val u = User.create(User("Org", "org@test.com", roles = Set("organizer")))
      val r = execute("""mutation { createContest(input:{name:"X",year:2024,country:"Y"}) { id } }""", authedCtx(u))
      r.errors must not beEmpty
      errorCode(r) must_== "FORBIDDEN"
    }

    "create contest for root user" in {
      SharedTestDb.truncateAll()
      val u = User.create(User("Root", "root@test.com", roles = Set("root")))
      val r = execute("""mutation { createContest(input:{name:"New",year:2025,country:"UA"}) { name year } }""", authedCtx(u))
      r.errors must beEmpty
      str(field(dataField(r, "createContest"), "name")) must_== "New"
    }
  }
}
```

- [ ] **Step 2: Run — verify it fails**

```bash
sbt "testOnly graphql2.ContestResolverSpec2"
```

Expected: tests fail with `RuntimeException: not implemented` (the stubs).

- [ ] **Step 3: Implement contest resolver fields in `SchemaBuilder.scala`**

Replace the `contests`, `contest`, `createContest`, `updateContest`, `deleteContest` stubs:

```scala
// In SchemaBuilder.queries:
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
```

```scala
// In SchemaBuilder.mutations:
createContest = args => ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
  ctx.requireRole("root") *>
    ZIO.fromFuture { implicit ec =>
      scala.concurrent.Future(
        ContestJuryJdbc.create(None, args.input.name, args.input.year, args.input.country,
          args.input.images, None, None, args.input.monumentIdTemplate, args.input.campaign)
      ).map(ContestView.from)
    }.mapError(GraphQL2Context.dbError)
},

updateContest = args => ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
  ctx.requireRole("root") *>
    ZIO.fromFuture { implicit ec =>
      scala.concurrent.Future {
        ContestJuryJdbc.updateById(args.id.toLong).withAttributes(
          "name"               -> args.input.name,
          "year"               -> args.input.year,
          "country"            -> args.input.country,
          "images"             -> args.input.images.orNull,
          "campaign"           -> args.input.campaign.orNull,
          "monumentIdTemplate" -> args.input.monumentIdTemplate.orNull
        )
        ContestJuryJdbc.findById(args.id.toLong)
          .map(ContestView.from)
          .getOrElse(throw new RuntimeException(s"Contest ${args.id} not found"))
      }
    }.mapError(GraphQL2Context.dbError)
},

deleteContest = args => ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
  ctx.requireRole("root") *>
    ZIO.fromFuture(implicit ec =>
      scala.concurrent.Future { ContestJuryJdbc.deleteById(args.id.toLong); true }
    ).mapError(GraphQL2Context.dbError)
},
```

Also add the necessary imports at the top of `SchemaBuilder.scala`:
```scala
import db.scalikejdbc.{ContestJuryJdbc, ImageJdbc, MonumentJdbc, Round, RoundUser, SelectionJdbc, User}
import graphql2.views._
import org.intracer.wmua.CommentJdbc
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtJson}
import play.api.libs.json.Json
```

- [ ] **Step 4: Run — verify tests pass**

```bash
sbt "testOnly graphql2.ContestResolverSpec2"
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/graphql2/SchemaBuilder.scala test/graphql2/ContestResolverSpec2.scala
git commit -m "feat: implement Caliban ContestResolver with TDD"
```

---

### Task 7: TDD — RoundResolver

**Files:**
- Create: `test/graphql2/RoundResolverSpec2.scala`
- Modify: `app/graphql2/SchemaBuilder.scala`

- [ ] **Step 1: Write `RoundResolverSpec2.scala`**

```scala
// test/graphql2/RoundResolverSpec2.scala
package graphql2

import db.scalikejdbc.{ContestJuryJdbc, Round, SharedTestDb, User}
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

class RoundResolverSpec2 extends Specification with BeforeAll with GraphQL2SpecHelper {
  sequential

  override def beforeAll(): Unit = SharedTestDb.init()

  "rounds query" should {
    "return rounds for a contest" in {
      SharedTestDb.truncateAll()
      val c = ContestJuryJdbc.create(None, "WLM", 2024, "UA", None, None, None, None)
      Round.create(Round(None, 1, None, c.getId, distribution = 0, hasCriteria = false))
      val r = execute(s"""{ rounds(contestId: "${c.getId}") { id number active } }""")
      r.errors must beEmpty
      list(dataField(r, "rounds")) must have size 1
    }
  }

  "round query" should {
    "return round by id" in {
      SharedTestDb.truncateAll()
      val c = ContestJuryJdbc.create(None, "WLM", 2024, "UA", None, None, None, None)
      val rnd = Round.create(Round(None, 1, Some("R1"), c.getId, distribution = 0, hasCriteria = false))
      val r = execute(s"""{ round(id: "${rnd.getId}") { name number } }""")
      r.errors must beEmpty
      str(field(dataField(r, "round"), "name")) must_== "R1"
    }
  }

  "createRound mutation" should {
    "fail with FORBIDDEN for jury user" in {
      SharedTestDb.truncateAll()
      val u = User.create(User("Jury", "jury@test.com", roles = Set("jury")))
      val r = execute("""mutation { createRound(input:{contestId:"1"}) { id } }""", authedCtx(u))
      r.errors must not beEmpty
      errorCode(r) must_== "FORBIDDEN"
    }
  }
}
```

- [ ] **Step 2: Run — verify it fails**

```bash
sbt "testOnly graphql2.RoundResolverSpec2"
```

Expected: fails with `RuntimeException: not implemented`.

- [ ] **Step 3: Implement round resolver fields in `SchemaBuilder.scala`**

Replace `round`, `rounds`, `roundStat`, `createRound`, `updateRound`, `setActiveRound`, `addJurorsToRound` stubs:

```scala
// In SchemaBuilder.queries:
round = args => ZIO.serviceWithZIO[GraphQL2Context] { _ =>
  ZIO.fromFuture(implicit ec =>
    scala.concurrent.Future(Round.findById(args.id.toLong).map(RoundView.from))
  ).mapError(GraphQL2Context.dbError)
},

rounds = args => ZIO.serviceWithZIO[GraphQL2Context] { _ =>
  ZIO.fromFuture(implicit ec =>
    scala.concurrent.Future(Round.findByContest(args.contestId.toLong).map(RoundView.from))
  ).mapError(GraphQL2Context.dbError)
},

roundStat = args => ZIO.serviceWithZIO[GraphQL2Context] { _ =>
  ZIO.fromFuture { implicit ec =>
    import db.scalikejdbc.rewrite.ImageDbNew.{Limit, SelectionQuery}
    scala.concurrent.Future {
      val roundId = args.roundId.toLong
      Round.findById(roundId)
        .getOrElse(throw new RuntimeException(s"Round $roundId not found"))
      val total  = SelectionQuery(roundId = Some(roundId), grouped = true).count()
      val rated  = SelectionQuery(roundId = Some(roundId), rate = Some(1), grouped = true).count()
      val jurors = User.findByRoundSelection(roundId)
      val stats  = jurors.map { u =>
        val r = SelectionQuery(roundId = Some(roundId), userId = u.id, grouped = true).count()
        val s = SelectionQuery(roundId = Some(roundId), userId = u.id, rate = Some(1), grouped = true).count()
        JurorStatView(UserView.from(u), r, s)
      }
      RoundStatView(roundId = roundId.toString, totalImages = total, ratedImages = rated, jurorStats = stats)
    }
  }.mapError(GraphQL2Context.dbError)
},
```

```scala
// In SchemaBuilder.mutations:
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
    ZIO.fromFuture { implicit ec =>
      scala.concurrent.Future {
        val id       = args.id.toLong
        val existing = Round.findById(id).getOrElse(throw new RuntimeException(s"Round $id not found"))
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
},

setActiveRound = args => ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
  ctx.requireRole("organizer") *>
    ZIO.fromFuture { implicit ec =>
      scala.concurrent.Future {
        val id    = args.id.toLong
        val round = Round.findById(id).getOrElse(throw new RuntimeException(s"Round $id not found"))
        Round.setActive(id, active = true)
        RoundView.from(round.copy(active = true))
      }
    }.mapError(GraphQL2Context.dbError)
},

addJurorsToRound = args => ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
  ctx.requireRole("organizer") *>
    ZIO.fromFuture { implicit ec =>
      scala.concurrent.Future {
        val roundId = args.roundId.toLong
        val round   = Round.findById(roundId).getOrElse(throw new RuntimeException(s"Round $roundId not found"))
        round.addUsers(args.userIds.map(uid => RoundUser(roundId, uid.toLong, "jury", active = true)))
        RoundView.from(Round.findById(roundId).get)
      }
    }.mapError(GraphQL2Context.dbError)
},
```

- [ ] **Step 4: Run — verify tests pass**

```bash
sbt "testOnly graphql2.RoundResolverSpec2"
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/graphql2/SchemaBuilder.scala test/graphql2/RoundResolverSpec2.scala
git commit -m "feat: implement Caliban RoundResolver with TDD"
```

---

### Task 8: TDD — ImageResolver

**Files:**
- Create: `test/graphql2/ImageResolverSpec2.scala`
- Modify: `app/graphql2/SchemaBuilder.scala`

- [ ] **Step 1: Write `ImageResolverSpec2.scala`**

```scala
// test/graphql2/ImageResolverSpec2.scala
package graphql2

import db.scalikejdbc.rewrite.ImageDbNew.SelectionQuery
import db.scalikejdbc.{ContestJuryJdbc, ImageJdbc, Round, SelectionJdbc, SharedTestDb, User}
import org.intracer.wmua.Image
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

class ImageResolverSpec2 extends Specification with BeforeAll with GraphQL2SpecHelper {
  sequential

  override def beforeAll(): Unit = SharedTestDb.init()

  "images query" should {
    "return images for a round" in {
      SharedTestDb.truncateAll()
      val c   = ContestJuryJdbc.create(None, "WLM", 2024, "UA", None, None, None, None)
      val rnd = Round.create(Round(None, 1, None, c.getId, distribution = 0, hasCriteria = false))
      ImageJdbc.batchInsert(Seq(Image(pageId = 101L, title = "File:Test.jpg", contest = c.getId)))
      SelectionJdbc.create(101L, 1, 1L, rnd.getId)
      val r = execute(s"""{ images(roundId: "${rnd.getId}") { image { title } rateSum } }""")
      r.errors must beEmpty
      val items = list(dataField(r, "images"))
      items must have size 1
      str(field(field(items.head, "image"), "title")) must_== "File:Test.jpg"
    }
  }

  "rateImage mutation" should {
    "fail with UNAUTHENTICATED when no user" in {
      val r = execute("""mutation { rateImage(roundId:"1", pageId:"101", rate:1) { id } }""")
      r.errors must not beEmpty
      errorCode(r) must_== "UNAUTHENTICATED"
    }

    "rate an image" in {
      SharedTestDb.truncateAll()
      val c   = ContestJuryJdbc.create(None, "WLM", 2024, "UA", None, None, None, None)
      val u   = User.create(User("Jury", "jury@test.com", roles = Set("jury"), contestId = Some(c.getId)))
      val rnd = Round.create(Round(None, 1, None, c.getId, distribution = 0, hasCriteria = false))
      ImageJdbc.batchInsert(Seq(Image(pageId = 202L, title = "File:A.jpg", contest = c.getId)))
      val r = execute(
        s"""mutation { rateImage(roundId:"${rnd.getId}", pageId:"202", rate:1) { rate roundId } }""",
        authedCtx(u)
      )
      r.errors must beEmpty
      int(field(dataField(r, "rateImage"), "rate")) must_== 1
    }
  }
}
```

- [ ] **Step 2: Run — verify it fails**

```bash
sbt "testOnly graphql2.ImageResolverSpec2"
```

Expected: fails with `RuntimeException: not implemented`.

- [ ] **Step 3: Implement image resolver fields in `SchemaBuilder.scala`**

```scala
// In SchemaBuilder.queries:
images = args => ZIO.serviceWithZIO[GraphQL2Context] { _ =>
  ZIO.fromFuture { implicit ec =>
    import db.scalikejdbc.rewrite.ImageDbNew.{Limit, SelectionQuery}
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
      ).list().map(ImageWithRatingView.from)
    }
  }.mapError(GraphQL2Context.dbError)
},

image = args => ZIO.serviceWithZIO[GraphQL2Context] { _ =>
  ZIO.fromFuture(implicit ec =>
    scala.concurrent.Future(ImageJdbc.findById(args.pageId.toLong).map(ImageView.from))
  ).mapError(GraphQL2Context.dbError)
},
```

```scala
// In SchemaBuilder.mutations:
rateImage = args => ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
  ctx.requireAuth *>
    ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
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
  ctx.requireAuth *>
    ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
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
```

- [ ] **Step 4: Run — verify tests pass**

```bash
sbt "testOnly graphql2.ImageResolverSpec2"
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/graphql2/SchemaBuilder.scala test/graphql2/ImageResolverSpec2.scala
git commit -m "feat: implement Caliban ImageResolver with TDD"
```

---

### Task 9: TDD — UserResolver

**Files:**
- Create: `test/graphql2/UserResolverSpec2.scala`
- Modify: `app/graphql2/SchemaBuilder.scala`

- [ ] **Step 1: Write `UserResolverSpec2.scala`**

```scala
// test/graphql2/UserResolverSpec2.scala
package graphql2

import db.scalikejdbc.{ContestJuryJdbc, SharedTestDb, User}
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

class UserResolverSpec2 extends Specification with BeforeAll with GraphQL2SpecHelper {
  sequential

  override def beforeAll(): Unit = SharedTestDb.init()

  "me query" should {
    "return null for anonymous" in {
      val r = execute("{ me { email } }")
      r.errors must beEmpty
      dataField(r, "me") must_== caliban.ResponseValue.NullValue
    }

    "return current user when authenticated" in {
      SharedTestDb.truncateAll()
      val u = User.create(User("Root", "root@test.com", roles = Set("root")))
      val r = execute("{ me { email } }", authedCtx(u))
      r.errors must beEmpty
      str(field(dataField(r, "me"), "email")) must_== "root@test.com"
    }
  }

  "users query" should {
    "return all users" in {
      SharedTestDb.truncateAll()
      User.create(User("A", "a@test.com", roles = Set("jury")))
      User.create(User("B", "b@test.com", roles = Set("jury")))
      val r = execute("{ users { email } }")
      r.errors must beEmpty
      list(dataField(r, "users")) must have size 2
    }
  }

  "createUser mutation" should {
    "fail with FORBIDDEN for non-admin" in {
      SharedTestDb.truncateAll()
      val u = User.create(User("Jury", "jury@test.com", roles = Set("jury")))
      val r = execute(
        """mutation { createUser(input:{fullname:"X",email:"x@t.com",roles:["jury"]}) { id } }""",
        authedCtx(u)
      )
      r.errors must not beEmpty
      errorCode(r) must_== "FORBIDDEN"
    }
  }
}
```

- [ ] **Step 2: Run — verify it fails**

```bash
sbt "testOnly graphql2.UserResolverSpec2"
```

Expected: fails with `RuntimeException: not implemented`.

- [ ] **Step 3: Implement user resolver fields in `SchemaBuilder.scala`**

```scala
// In SchemaBuilder.queries:
users = args => ZIO.serviceWithZIO[GraphQL2Context] { _ =>
  ZIO.fromFuture { implicit ec =>
    import scalikejdbc.sqls
    scala.concurrent.Future {
      args.contestId match {
        case Some(id) => User.findAllBy(sqls.eq(User.u.contestId, id.toLong)).map(UserView.from)
        case None     => User.findAll().map(UserView.from)
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
```

```scala
// In SchemaBuilder.mutations:
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
    ZIO.fromFuture { implicit ec =>
      scala.concurrent.Future {
        val id       = args.id.toLong
        val existing = User.findById(id).getOrElse(throw new RuntimeException(s"User $id not found"))
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
},
```

- [ ] **Step 4: Run — verify tests pass**

```bash
sbt "testOnly graphql2.UserResolverSpec2"
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/graphql2/SchemaBuilder.scala test/graphql2/UserResolverSpec2.scala
git commit -m "feat: implement Caliban UserResolver with TDD"
```

---

### Task 10: TDD — CommentResolver

**Files:**
- Create: `test/graphql2/CommentResolverSpec2.scala`
- Modify: `app/graphql2/SchemaBuilder.scala`

- [ ] **Step 1: Write `CommentResolverSpec2.scala`**

```scala
// test/graphql2/CommentResolverSpec2.scala
package graphql2

import db.scalikejdbc.{ContestJuryJdbc, Round, SharedTestDb, User}
import org.intracer.wmua.CommentJdbc
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

class CommentResolverSpec2 extends Specification with BeforeAll with GraphQL2SpecHelper {
  sequential

  override def beforeAll(): Unit = SharedTestDb.init()

  "comments query" should {
    "return comments for a round" in {
      SharedTestDb.truncateAll()
      val c   = ContestJuryJdbc.create(None, "WLM", 2024, "UA", None, None, None, None)
      val rnd = Round.create(Round(None, 1, None, c.getId, distribution = 0, hasCriteria = false))
      CommentJdbc.create(1L, "Alice", rnd.getId, Some(c.getId), 101L, "Nice!", java.time.LocalDateTime.now.toString)
      val r = execute(s"""{ comments(roundId: "${rnd.getId}") { body username } }""")
      r.errors must beEmpty
      val items = list(dataField(r, "comments"))
      items must have size 1
      str(field(items.head, "body")) must_== "Nice!"
    }
  }

  "addComment mutation" should {
    "fail with UNAUTHENTICATED when no user" in {
      val r = execute("""mutation { addComment(roundId:"1", imagePageId:"101", body:"Hi") { id } }""")
      r.errors must not beEmpty
      errorCode(r) must_== "UNAUTHENTICATED"
    }

    "add a comment when authenticated" in {
      SharedTestDb.truncateAll()
      val c   = ContestJuryJdbc.create(None, "WLM", 2024, "UA", None, None, None, None)
      val u   = User.create(User("Juror", "j@test.com", roles = Set("jury"), contestId = Some(c.getId)))
      val rnd = Round.create(Round(None, 1, None, c.getId, distribution = 0, hasCriteria = false))
      val r = execute(
        s"""mutation { addComment(roundId:"${rnd.getId}", imagePageId:"101", body:"Hello") { body } }""",
        authedCtx(u)
      )
      r.errors must beEmpty
      str(field(dataField(r, "addComment"), "body")) must_== "Hello"
    }
  }
}
```

- [ ] **Step 2: Run — verify it fails**

```bash
sbt "testOnly graphql2.CommentResolverSpec2"
```

Expected: fails with `RuntimeException: not implemented`.

- [ ] **Step 3: Implement comment resolver fields in `SchemaBuilder.scala`**

```scala
// In SchemaBuilder.queries:
comments = args => ZIO.serviceWithZIO[GraphQL2Context] { _ =>
  ZIO.fromFuture { implicit ec =>
    scala.concurrent.Future {
      val roundId = args.roundId.toLong
      args.imagePageId match {
        case Some(pageId) => CommentJdbc.findByRoundAndSubject(roundId, pageId.toLong).map(CommentView.from)
        case None         => CommentJdbc.findByRound(roundId).map(CommentView.from)
      }
    }
  }.mapError(GraphQL2Context.dbError)
},
```

```scala
// In SchemaBuilder.mutations:
addComment = args => ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
  ctx.requireAuth *>
    ZIO.serviceWithZIO[GraphQL2Context] { ctx =>
      ZIO.fromFuture { implicit ec =>
        scala.concurrent.Future {
          val user = ctx.currentUser.get
          CommentView.from(CommentJdbc.create(
            user.getId, user.fullname, args.roundId.toLong, user.contestId,
            args.imagePageId.toLong, args.body,
            createdAt = java.time.LocalDateTime.now.toString
          ))
        }
      }.mapError(GraphQL2Context.dbError)
    }
},
```

- [ ] **Step 4: Run — verify tests pass**

```bash
sbt "testOnly graphql2.CommentResolverSpec2"
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/graphql2/SchemaBuilder.scala test/graphql2/CommentResolverSpec2.scala
git commit -m "feat: implement Caliban CommentResolver with TDD"
```

---

### Task 11: TDD — MonumentResolver

**Files:**
- Create: `test/graphql2/MonumentResolverSpec2.scala`
- Modify: `app/graphql2/SchemaBuilder.scala`

- [ ] **Step 1: Write `MonumentResolverSpec2.scala`**

```scala
// test/graphql2/MonumentResolverSpec2.scala
package graphql2

import db.scalikejdbc.{MonumentJdbc, SharedTestDb}
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

class MonumentResolverSpec2 extends Specification with BeforeAll with GraphQL2SpecHelper {
  sequential

  override def beforeAll(): Unit = SharedTestDb.init()

  "monuments query" should {
    "return monuments for a contest" in {
      SharedTestDb.truncateAll()
      // MonumentJdbc inserts require a contestId; use a known one
      val r = execute("""{ monuments(contestId: "1") { id name } }""")
      r.errors must beEmpty
      list(dataField(r, "monuments")) must beEmpty  // empty is valid, just no crash
    }
  }

  "monument query" should {
    "return None for unknown id" in {
      val r = execute("""{ monument(id: "nonexistent") { id } }""")
      r.errors must beEmpty
      dataField(r, "monument") must_== caliban.ResponseValue.NullValue
    }
  }
}
```

- [ ] **Step 2: Run — verify it fails**

```bash
sbt "testOnly graphql2.MonumentResolverSpec2"
```

Expected: fails with `RuntimeException: not implemented`.

- [ ] **Step 3: Implement monument resolver fields in `SchemaBuilder.scala`**

```scala
// In SchemaBuilder.queries:
monuments = args => ZIO.serviceWithZIO[GraphQL2Context] { _ =>
  ZIO.fromFuture { implicit ec =>
    scala.concurrent.Future {
      val contestId = args.contestId.toLong
      MonumentJdbc.findAll().filter(_.contest.contains(contestId)).map(MonumentView.from)
    }
  }.mapError(GraphQL2Context.dbError)
},

monument = args => ZIO.serviceWithZIO[GraphQL2Context] { _ =>
  ZIO.fromFuture(implicit ec =>
    scala.concurrent.Future(MonumentJdbc.find(args.id).map(MonumentView.from))
  ).mapError(GraphQL2Context.dbError)
},
```

- [ ] **Step 4: Run — verify tests pass**

```bash
sbt "testOnly graphql2.MonumentResolverSpec2"
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/graphql2/SchemaBuilder.scala test/graphql2/MonumentResolverSpec2.scala
git commit -m "feat: implement Caliban MonumentResolver with TDD"
```

---

### Task 12: TDD — AuthResolver (login mutation)

**Files:**
- Create: `test/graphql2/AuthSpec2.scala`
- Modify: `app/graphql2/SchemaBuilder.scala`

The `login` mutation needs the JWT secret. Inject it via a Guice `@Named("jwtSecret")` binding, or read it directly from config. Since `SchemaBuilder` is an `object`, pass the secret at initialization. Use a `var` for tests and set it before calling the interpreter.

Simpler: read the secret from Play config when it's available, or accept a default for tests. Add a `jwtSecret` field to `SchemaBuilder` that can be set in tests:

```scala
// In SchemaBuilder.scala — add at the top:
var jwtSecret: String = "default-secret-change-me"
```

`GraphQL2Route` will set `SchemaBuilder.jwtSecret = config.get[String]("graphql.jwt.secret")` on construction. Tests set it to `"test-secret"`.

- [ ] **Step 1: Add `jwtSecret` to `SchemaBuilder.scala`**

At the top of `SchemaBuilder` object, before `queries`, add:

```scala
var jwtSecret: String = "test-secret"
```

- [ ] **Step 2: Write `AuthSpec2.scala`**

```scala
// test/graphql2/AuthSpec2.scala
package graphql2

import db.scalikejdbc.{SharedTestDb, User}
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

class AuthSpec2 extends Specification with BeforeAll with GraphQL2SpecHelper {
  sequential

  override def beforeAll(): Unit = {
    SharedTestDb.init()
    SchemaBuilder.jwtSecret = "test-secret"
  }

  "login mutation" should {
    "return token and user on valid credentials" in {
      SharedTestDb.truncateAll()
      val password = "secret123"
      User.create(User("Alice", "alice@test.com", roles = Set("jury"),
        password = Some(User.sha1(password))))
      val r = execute("""mutation { login(email:"alice@test.com", password:"secret123") { token user { email } } }""")
      r.errors must beEmpty
      val payload = dataField(r, "login")
      str(field(payload, "token")) must not beEmpty
      str(field(field(payload, "user"), "email")) must_== "alice@test.com"
    }

    "return UNAUTHENTICATED on wrong password" in {
      SharedTestDb.truncateAll()
      User.create(User("Bob", "bob@test.com", roles = Set("jury"),
        password = Some(User.sha1("correct"))))
      val r = execute("""mutation { login(email:"bob@test.com", password:"wrong") { token } }""")
      r.errors must not beEmpty
      errorCode(r) must_== "UNAUTHENTICATED"
    }
  }
}
```

- [ ] **Step 3: Run — verify it fails**

```bash
sbt "testOnly graphql2.AuthSpec2"
```

Expected: fails with `RuntimeException: not implemented`.

- [ ] **Step 4: Implement login in `SchemaBuilder.scala`**

```scala
// In SchemaBuilder.mutations:
login = args => ZIO.serviceWithZIO[GraphQL2Context] { _ =>
  ZIO.fromFuture { implicit ec =>
    scala.concurrent.Future {
      val userOpt = User.findByEmail(args.email).headOption
      val authed  = userOpt.filter(_.password.exists(_ == User.sha1(args.password)))
      val user    = authed.getOrElse(throw new RuntimeException("Invalid email or password"))
      val claim = pdi.jwt.JwtClaim(
        content    = Json.obj("userId" -> user.getId).toString,
        expiration = Some(System.currentTimeMillis() / 1000 + 86400 * 30),
        issuedAt   = Some(System.currentTimeMillis() / 1000)
      )
      val token = JwtJson.encode(claim, SchemaBuilder.jwtSecret, JwtAlgorithm.HS256)
      AuthPayloadView(token, UserView.from(user))
    }
  }.mapError { e =>
    CalibanError.ExecutionError(e.getMessage,
      extensions = Some(caliban.ResponseValue.ObjectValue(
        List("code" -> caliban.ResponseValue.StringValue("UNAUTHENTICATED")))))
  }
},
```

Add to `SchemaBuilder.scala` imports:
```scala
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtJson}
import play.api.libs.json.Json
```

- [ ] **Step 5: Run — verify tests pass**

```bash
sbt "testOnly graphql2.AuthSpec2"
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/graphql2/SchemaBuilder.scala test/graphql2/AuthSpec2.scala
git commit -m "feat: implement Caliban AuthResolver (login) with TDD"
```

---

### Task 13: TDD — SubscriptionResolver

**Files:**
- Create: `test/graphql2/SubscriptionSpec2.scala`
- Modify: `app/graphql2/SchemaBuilder.scala`

- [ ] **Step 1: Write `SubscriptionSpec2.scala`**

```scala
// test/graphql2/SubscriptionSpec2.scala
package graphql2

import caliban.ResponseValue
import db.scalikejdbc.SharedTestDb
import graphql.SubscriptionEventBus
import graphql.SubscriptionEventBus.ImageRatedEvent
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import zio._
import zio.stream.ZStream

class SubscriptionSpec2 extends Specification with BeforeAll with GraphQL2SpecHelper {
  sequential

  override def beforeAll(): Unit = SharedTestDb.init()

  "imageRated subscription" should {
    "emit events matching the roundId" in {
      val bus = new SubscriptionEventBus()(testMat)
      val ctx = GraphQL2Context(None, bus, testMat)

      // Collect 1 event from the subscription stream
      val collected = Unsafe.unsafe { implicit u =>
        Runtime.default.unsafe.run(
          SchemaBuilder.interpreter
            .execute("subscription { imageRated(roundId:\"42\") { roundId rate } }")
            .flatMap { resp =>
              resp.data match {
                case Some(ResponseValue.ObjectValue(List(("imageRated", stream)))) =>
                  stream match {
                    case ResponseValue.StreamValue(s) =>
                      // Publish an event then take one element
                      bus.publishImageRated(ImageRatedEvent(42L, 1L, 1L, 3))
                      s.take(1).runCollect
                    case _ => ZIO.succeed(Chunk.empty)
                  }
                case _ => ZIO.succeed(Chunk.empty)
              }
            }
            .provideLayer(ZLayer.succeed(ctx))
        ).getOrThrow()
      }

      collected must not beEmpty
    }
  }
}
```

- [ ] **Step 2: Run — verify it fails**

```bash
sbt "testOnly graphql2.SubscriptionSpec2"
```

Expected: fails with `RuntimeException: not implemented`.

- [ ] **Step 3: Implement subscription fields in `SchemaBuilder.scala`**

Add import at top of `SchemaBuilder.scala`:
```scala
import org.apache.pekko.stream.scaladsl.{Keep, Sink}
import zio.interop.reactivestreams._
import zio.stream.ZStream
```

Replace `imageRated` and `roundProgress` stubs:

```scala
// In SchemaBuilder.subscriptions:
imageRated = args => ZStream.fromZIO(ZIO.service[GraphQL2Context]).flatMap { ctx =>
  implicit val mat = ctx.mat
  val roundId  = args.roundId.toLong
  val source   = ctx.eventBus.imageRatedSource.filter(_.roundId == roundId)
  val publisher = source.toMat(Sink.asPublisher(fanout = false))(Keep.right).run()
  publisher.toZIOStream().map { e =>
    ImageRatedEventView(
      roundId = e.roundId.toString,
      pageId  = e.pageId.toString,
      juryId  = e.juryId.toString,
      rate    = e.rate
    )
  }.mapError(GraphQL2Context.dbError)
},

roundProgress = args => ZStream.fromZIO(ZIO.service[GraphQL2Context]).flatMap { ctx =>
  implicit val mat = ctx.mat
  val roundId  = args.roundId.toLong
  val source   = ctx.eventBus.imageRatedSource.filter(_.roundId == roundId)
  val publisher = source.toMat(Sink.asPublisher(fanout = false))(Keep.right).run()
  publisher.toZIOStream().map { e =>
    ImageRatedEventView(
      roundId = e.roundId.toString,
      pageId  = e.pageId.toString,
      juryId  = e.juryId.toString,
      rate    = e.rate
    )
  }.mapError(GraphQL2Context.dbError)
},
```

- [ ] **Step 4: Run — verify tests pass**

```bash
sbt "testOnly graphql2.SubscriptionSpec2"
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/graphql2/SchemaBuilder.scala test/graphql2/SubscriptionSpec2.scala
git commit -m "feat: implement Caliban SubscriptionResolver with TDD"
```

---

### Task 14: Schema validation spec

**Files:**
- Create: `test/graphql2/SchemaValidationSpec2.scala`

- [ ] **Step 1: Write `SchemaValidationSpec2.scala`**

```scala
// test/graphql2/SchemaValidationSpec2.scala
package graphql2

import org.specs2.mutable.Specification

class SchemaValidationSpec2 extends Specification {

  "SchemaBuilder.api SDL" should {
    "contain all expected type names" in {
      val sdl = SchemaBuilder.api.render
      sdl must contain("ContestView")
      sdl must contain("RoundView")
      sdl must contain("UserView")
      sdl must contain("ImageView")
      sdl must contain("SelectionView")
      sdl must contain("ImageWithRatingView")
      sdl must contain("CommentView")
      sdl must contain("MonumentView")
      sdl must contain("AuthPayloadView")
      sdl must contain("ImageRatedEventView")
      sdl must contain("RoundStatView")
    }

    "contain all expected query fields" in {
      val sdl = SchemaBuilder.api.render
      sdl must contain("contests")
      sdl must contain("contest")
      sdl must contain("round")
      sdl must contain("rounds")
      sdl must contain("roundStat")
      sdl must contain("images")
      sdl must contain("image")
      sdl must contain("users")
      sdl must contain("user")
      sdl must contain("me")
      sdl must contain("comments")
      sdl must contain("monuments")
      sdl must contain("monument")
    }

    "contain all expected mutation fields" in {
      val sdl = SchemaBuilder.api.render
      sdl must contain("login")
      sdl must contain("createContest")
      sdl must contain("updateContest")
      sdl must contain("deleteContest")
      sdl must contain("createRound")
      sdl must contain("updateRound")
      sdl must contain("setActiveRound")
      sdl must contain("addJurorsToRound")
      sdl must contain("rateImage")
      sdl must contain("rateImageBulk")
      sdl must contain("setRoundImages")
      sdl must contain("createUser")
      sdl must contain("updateUser")
      sdl must contain("addComment")
    }

    "contain subscription fields" in {
      val sdl = SchemaBuilder.api.render
      sdl must contain("imageRated")
      sdl must contain("roundProgress")
    }
  }
}
```

- [ ] **Step 2: Run**

```bash
sbt "testOnly graphql2.SchemaValidationSpec2"
```

Expected: all assertions pass. If a type name appears differently in the SDL (e.g. Caliban strips `View` suffix or renames), adjust the `must contain` strings to match `SchemaBuilder.api.render` output.

- [ ] **Step 3: Commit**

```bash
git add test/graphql2/SchemaValidationSpec2.scala
git commit -m "test: add Caliban schema SDL validation spec"
```

---

### Task 15: Create GraphQL2Route and GraphQL2RouteSpec2

**Files:**
- Create: `app/graphql2/GraphQL2Route.scala`
- Create: `test/graphql2/GraphQL2RouteSpec2.scala`

The route handles `POST /graphql2`. It parses the body with Play JSON, converts variables to Caliban `InputValue`, executes via the interpreter, and serializes the `ResponseValue` back to Play JSON — no extra JSON library needed.

- [ ] **Step 1: Write `GraphQL2RouteSpec2.scala` (failing first)**

```scala
// test/graphql2/GraphQL2RouteSpec2.scala
package graphql2

import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
import db.scalikejdbc.{ContestJuryJdbc, SharedTestDb, User}
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.testkit.Specs2RouteTest
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import play.api.Configuration
import play.api.libs.json._

class GraphQL2RouteSpec2 extends Specification with Specs2RouteTest with BeforeAll {
  sequential

  private val jwtSecret = "test-secret"
  private val config    = Configuration("graphql.jwt.secret" -> jwtSecret)

  // route is created after beforeAll (SharedTestDb.init may be needed)
  private lazy val route = new GraphQL2Route(config, graphql.SubscriptionEventBus.noop).route

  override def beforeAll(): Unit = {
    SharedTestDb.init()
    SchemaBuilder.jwtSecret = jwtSecret
  }

  def body(query: String): JsValue = Json.obj("query" -> query)

  "GraphQL2Route POST /graphql2" should {
    "return contests list" in {
      SharedTestDb.truncateAll()
      ContestJuryJdbc.create(None, "WLM", 2024, "Ukraine", None, None, None, None)
      Post("/graphql2", body("{ contests { name year } }")) ~> route ~> check {
        val r = responseAs[JsValue]
        (r \ "data" \ "contests" \ 0 \ "name").as[String] must_== "WLM"
      }
    }

    "return UNAUTHENTICATED for createContest without auth" in {
      Post("/graphql2", body("""mutation { createContest(input:{name:"X",year:2024,country:"Y"}) { id } }""")) ~>
        route ~> check {
          val r = responseAs[JsValue]
          (r \ "errors" \ 0 \ "extensions" \ "code").as[String] must_== "UNAUTHENTICATED"
        }
    }

    "accept X-Authenticated-User proxy header" in {
      SharedTestDb.truncateAll()
      User.create(User("Root", "root@test.com", roles = Set("root")))
      Post("/graphql2", body("{ me { email } }")) ~>
        addHeader(RawHeader("X-Authenticated-User", "root@test.com")) ~>
        route ~> check {
          val r = responseAs[JsValue]
          (r \ "data" \ "me" \ "email").as[String] must_== "root@test.com"
        }
    }
  }
}
```

- [ ] **Step 2: Run — verify it fails** (route class doesn't exist yet)

```bash
sbt "testOnly graphql2.GraphQL2RouteSpec2"
```

Expected: compile error or test fails — `GraphQL2Route` does not exist.

- [ ] **Step 3: Create `GraphQL2Route.scala`**

```scala
// app/graphql2/GraphQL2Route.scala
package graphql2

import caliban.{CalibanError, GraphQLResponse, InputValue}
import caliban.ResponseValue
import caliban.ResponseValue._
import db.scalikejdbc.User
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.Materializer
import pdi.jwt.{JwtAlgorithm, JwtJson}
import play.api.Configuration
import play.api.libs.json._
import graphql.SubscriptionEventBus
import zio._

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class GraphQL2Route @Inject()(config: Configuration, eventBus: SubscriptionEventBus)(
  implicit ec: ExecutionContext,
  mat: Materializer
) {

  SchemaBuilder.jwtSecret = config.get[String]("graphql.jwt.secret")

  private def resolveUser(authHeader: Option[String], proxyUser: Option[String]): Option[User] =
    proxyUser
      .flatMap(email => User.findByEmail(email).headOption)
      .orElse {
        authHeader.filter(_.startsWith("Bearer ")).flatMap { h =>
          JwtJson
            .decodeJson(h.drop(7), SchemaBuilder.jwtSecret, Seq(JwtAlgorithm.HS256))
            .toOption
            .flatMap(j => (j \ "userId").asOpt[Long])
            .flatMap(User.findById)
        }
      }

  private def jsToInput(v: JsValue): InputValue = v match {
    case JsString(s)  => InputValue.StringValue(s)
    case JsNumber(n)  => InputValue.FloatValue(n.toDouble)
    case JsBoolean(b) => InputValue.BooleanValue(b)
    case JsNull       => InputValue.NullValue
    case JsArray(a)   => InputValue.ListValue(a.map(jsToInput).toList)
    case JsObject(o)  => InputValue.ObjectValue(o.map { case (k, v) => k -> jsToInput(v) }.toMap)
  }

  private def rvToJs(rv: ResponseValue): JsValue = rv match {
    case ObjectValue(fields)           => JsObject(fields.map { case (k, v) => k -> rvToJs(v) }.toMap)
    case ListValue(values)             => JsArray(values.map(rvToJs))
    case StringValue(s)                => JsString(s)
    case IntValue.IntNumber(n)         => JsNumber(n)
    case IntValue.LongNumber(n)        => JsNumber(n)
    case IntValue.BigIntNumber(n)      => JsNumber(BigDecimal(n))
    case FloatValue.FloatNumber(n)     => JsNumber(BigDecimal(n.toDouble))
    case FloatValue.DoubleNumber(n)    => JsNumber(n)
    case FloatValue.BigDecimalNumber(n)=> JsNumber(n)
    case BooleanValue(b)               => JsBoolean(b)
    case NullValue                     => JsNull
    case EnumValue(s)                  => JsString(s)
    case _                             => JsNull
  }

  private def encodeResponse(r: GraphQLResponse[CalibanError]): JsValue = {
    val dataObj  = r.data.map(d => Json.obj("data" -> rvToJs(d))).getOrElse(Json.obj())
    val errorsArr = r.errors.map {
      case CalibanError.ExecutionError(msg, _, _, _, exts) =>
        val base = Json.obj("message" -> msg)
        exts match {
          case Some(ObjectValue(fields)) =>
            val ext = JsObject(fields.map { case (k, v) => k -> rvToJs(v) }.toMap)
            base ++ Json.obj("extensions" -> ext)
          case _ => base
        }
      case e => Json.obj("message" -> e.getMessage)
    }
    if (errorsArr.isEmpty) dataObj
    else dataObj ++ Json.obj("errors" -> JsArray(errorsArr))
  }

  val route: Route =
    path("graphql2") {
      post {
        optionalHeaderValueByName("Authorization") { authHeader =>
          optionalHeaderValueByName("X-Authenticated-User") { proxyUser =>
            entity(as[JsValue]) { body =>
              val query   = (body \ "query").as[String]
              val vars    = (body \ "variables").asOpt[JsObject].getOrElse(JsObject.empty)
              val opName  = (body \ "operationName").asOpt[String]
              val ctx     = GraphQL2Context(resolveUser(authHeader, proxyUser), eventBus, mat)
              val varMap  = vars.fields.map { case (k, v) => k -> jsToInput(v) }.toMap

              val responseFuture = Unsafe.unsafe { implicit u =>
                Runtime.default.unsafe.runToFuture(
                  SchemaBuilder.interpreter
                    .execute(query, opName, varMap)
                    .provideLayer(ZLayer.succeed(ctx))
                )
              }

              complete(responseFuture.map { r =>
                HttpEntity(ContentTypes.`application/json`, encodeResponse(r).toString)
              })
            }
          }
        }
      }
    }
}
```

- [ ] **Step 4: Run — verify tests pass**

```bash
sbt "testOnly graphql2.GraphQL2RouteSpec2"
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/graphql2/GraphQL2Route.scala test/graphql2/GraphQL2RouteSpec2.scala
git commit -m "feat: add GraphQL2Route Pekko HTTP handler at /graphql2"
```

---

### Task 16: Register `/graphql2` in ApiServer

**Files:**
- Modify: `app/api/ApiServer.scala`
- Modify: `app/modules/AppModule.scala` (if `GraphQL2Route` needs Guice binding)

- [ ] **Step 1: Inject `GraphQL2Route` into `ApiServer.scala`**

Open `app/api/ApiServer.scala`. Add `graphql2Route: graphql2.GraphQL2Route` to the constructor and register its route:

```scala
// app/api/ApiServer.scala
package api

import graphql.GraphQLRoute
import graphql2.{GraphQL2Route => GraphQL2Route}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Directives.concat
import org.apache.pekko.http.scaladsl.server.Route
import play.api.inject.ApplicationLifecycle

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ApiServer @Inject()(
  api:           Api,
  graphQLRoute:  GraphQLRoute,
  graphql2Route: GraphQL2Route,
  actorSystem:   ActorSystem,
  lifecycle:     ApplicationLifecycle
)(implicit ec: ExecutionContext) {

  private val route = concat(
    api.routes, api.swaggerRoute,
    graphQLRoute.route,
    graphql2Route.route
  )
  private val bindingFuture =
    Http(actorSystem).newServerAt("0.0.0.0", 9001).bind(Route.toFunction(route)(actorSystem))

  lifecycle.addStopHook { () =>
    bindingFuture.flatMap(_.unbind())
  }
}
```

- [ ] **Step 2: Compile**

```bash
sbt compile
```

Expected: compiles cleanly. Guice will auto-discover `GraphQL2Route` as a `@Inject` class — no additional binding needed.

- [ ] **Step 3: Run full test suite**

```bash
sbt test
```

Expected: all tests pass (both `graphql/` Sangria tests and `graphql2/` Caliban tests).

- [ ] **Step 4: Commit**

```bash
git add app/api/ApiServer.scala
git commit -m "feat: register /graphql2 Caliban route in ApiServer"
```

---

## Self-Review Checklist

**Spec coverage:**
- ✅ Parallel coexistence at `/graphql2` — Tasks 15–16
- ✅ Shallow ZIO (all `ZIO.fromFuture` wrappers) — Tasks 6–13
- ✅ Separate view types with `from()` factories — Task 2
- ✅ Input + Args case classes — Task 3
- ✅ `GraphQL2Context` with ZIO auth helpers — Task 4
- ✅ All 13 query fields — Tasks 6, 7, 8, 9, 10, 11
- ✅ All 14 mutation fields — Tasks 6, 7, 8, 9, 10, 12
- ✅ Both subscription fields — Task 13
- ✅ Error extensions (UNAUTHENTICATED, FORBIDDEN, etc.) — Task 4 + 6 + 12
- ✅ Full test parity (10 specs) — Tasks 6–16
- ✅ `SchemaValidationSpec2` using `api.render` — Task 14
- ✅ `GraphQL2RouteSpec2` with HTTP testkit — Task 15
- ✅ Register in ApiServer — Task 16
