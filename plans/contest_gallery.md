Plan: View All Images in a Contest

Context

Currently GalleryController.listGeneric always requires a specific round (roundId=0 resolves the active round, otherwise a specific round is required). There's no way to browse all images across all rounds of a
contest. roundId=0 with no active round throws a NoSuchElementException on maybeRound.get.

The goal is to add a "contest gallery" view that shows all images that have selections in any round of a contest, reusing the existing galleryByRate gallery UI.

Architecture

The SelectionQuery SQL builder in ImageDbNew.scala already supports roundId: Option[Long] — when None, no round filter is applied in the WHERE clause. The missing piece is scoping to a contest: without a round
filter, the query spans all contests. Adding contestId narrows it via a subquery on the rounds table.

The galleryByRate.scala.html template has two maybeRound.get calls that would throw with None. Both must be made None-safe.

Changes

1. app/db/scalikejdbc/rewrite/ImageDbNew.scala

Add contestId: Option[Long] = None field to SelectionQuery. In where(), append to conditions:
contestId.map(id => s"s.round_id IN (SELECT id FROM rounds WHERE contest_id = $id)")

2. app/services/GalleryService.scala

Add contestId: Option[Long] = None parameter to getQuery() and pass it to SelectionQuery(...).

3. app/controllers/GalleryController.scala

Add new action:
def contestGallery(contestId: Long, page: Int = 1, region: String = "all", rate: Option[Int] = None): EssentialAction =
withAuth() { user => implicit request =>
if (!user.hasRole("root") && !user.isInContest(Some(contestId))) {
onUnAuthorized(user)
} else {
val rounds = Round.findByContest(contestId)
val pager = pageOffset(page)
val query = galleryService.getQuery(
userId = 0, rate = rate, roundId = None,
pager = Some(pager), contestId = Some(contestId)
)
pager.setCount(query.count())
val files = galleryService.filesByUserId(query, pager, userDetails = false)
Ok(views.html.galleryByRate(
user, 0, files, pager, None, rounds,
rate, region, Nil, new RateDistribution(Map.empty), None, Some(contestId)
))
}
}

4. app/views/galleryByRate.scala.html

Add parameter: contestId: Option[Long] = None as the 12th parameter.

Fix showRateGalery helper (lines 17–40): change @defining(maybeRound.get) { round => to @maybeRound.map { round => so it renders nothing when round is None.

Fix outer block (lines 46–89): replace
@defining(maybeRound.get) { round =>
@defining(round.id.getOrElse(0)) { roundId =>
with
@defining(maybeRound.flatMap(_.id).map(_.toLong).getOrElse(0L)) { roundId =>
Then replace round.getId in the large-view link with roundId.

Update @main call (line 42) to pass contestId as the 12th positional arg.

5. app/views/main.scala.html

Add contestId: Option[Long] = None as the 12th parameter (before the content block). Update the @navbar call:
contestId = contestId.orElse(selectedRound.map(_.contestId))
This keeps admin nav links (Images, Users, Rounds) working in the contest gallery view. All existing callers omit this arg and use the default.

6. app/views/navbar.scala.html

In the admin section (@if(user.hasAnyRole(User.ADMIN_ROLES)), inside @for(cId <- contestId)), add:
 <li role="presentation">
   <a class="brand" href="@routes.GalleryController.contestGallery(cId)">@Messages("all.images")</a>
 </li>

7. conf/routes

GET  /contest/:contestId/gallery  @controllers.GalleryController.contestGallery(contestId: Long, page: Int ?= 1, region: String ?= "all", rate: Option[Int] ?= None)

8. conf/messages.en

all.images=All images

Key Design Decisions

- grouped = true is preserved (since userId = 0): rates are summed per image across all rounds, giving a meaningful "total jury support" score for sorting.
- byRegionStat() is skipped for the contest gallery (byReg = Nil) because byRegionStat() currently hardcodes roundId.get. This avoids the complexity of fixing it and is acceptable for the initial version.
- Star rating display is hidden when maybeRound = None because there's no single rating scale; images are still sorted by total summed rate.
- Large image links use roundId = 0, which the existing LargeViewController handles by looking up the active round for the user.

Verification

1. Run: sbt compile — should compile cleanly
2. Navigate to /contest/:contestId/gallery as an admin — should see all images across all rounds
3. Verify the "All images" link appears in the navbar admin section when viewing a round gallery
4. Verify existing round galleries still work (no regression in galleryByRate for non-None rounds)
5. Run: sbt test — existing tests should pass