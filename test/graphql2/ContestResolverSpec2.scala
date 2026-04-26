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
      r.errors must not(beEmpty)
      errorCode(r) must_== "UNAUTHENTICATED"
    }

    "fail with FORBIDDEN for non-root user" in {
      SharedTestDb.truncateAll()
      val u = User.create(User("Org", "org@test.com", roles = Set("organizer")))
      val r = execute("""mutation { createContest(input:{name:"X",year:2024,country:"Y"}) { id } }""", authedCtx(u))
      r.errors must not(beEmpty)
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
