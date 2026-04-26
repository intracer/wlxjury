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
      r.errors must not(beEmpty)
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
