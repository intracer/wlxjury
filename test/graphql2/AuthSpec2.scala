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
      str(field(payload, "token")) must not(beEmpty)
      str(field(field(payload, "user"), "email")) must_== "alice@test.com"
    }

    "return UNAUTHENTICATED on wrong password" in {
      SharedTestDb.truncateAll()
      User.create(User("Bob", "bob@test.com", roles = Set("jury"),
        password = Some(User.sha1("correct"))))
      val r = execute("""mutation { login(email:"bob@test.com", password:"wrong") { token } }""")
      r.errors must not(beEmpty)
      errorCode(r) must_== "UNAUTHENTICATED"
    }
  }
}
