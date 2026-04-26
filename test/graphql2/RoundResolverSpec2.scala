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
      r.errors must not(beEmpty)
      errorCode(r) must_== "FORBIDDEN"
    }
  }
}
