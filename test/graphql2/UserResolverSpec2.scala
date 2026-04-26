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
      dataField(r, "me") must_== caliban.Value.NullValue
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
      r.errors must not(beEmpty)
      errorCode(r) must_== "FORBIDDEN"
    }
  }
}
