// test/graphql2/MonumentResolverSpec2.scala
package graphql2

import db.scalikejdbc.SharedTestDb
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

class MonumentResolverSpec2 extends Specification with BeforeAll with GraphQL2SpecHelper {
  sequential

  override def beforeAll(): Unit = SharedTestDb.init()

  "monuments query" should {
    "return monuments for a contest" in {
      SharedTestDb.truncateAll()
      val r = execute("""{ monuments(contestId: "1") { id name } }""")
      r.errors must beEmpty
      list(dataField(r, "monuments")) must beEmpty
    }
  }

  "monument query" should {
    "return null for unknown id" in {
      val r = execute("""{ monument(id: "nonexistent") { id } }""")
      r.errors must beEmpty
      dataField(r, "monument") must_== caliban.Value.NullValue
    }
  }
}
