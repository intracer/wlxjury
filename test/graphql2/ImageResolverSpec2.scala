// test/graphql2/ImageResolverSpec2.scala
package graphql2

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
      ImageJdbc.batchInsert(Seq(Image(pageId = 101L, title = "File:Test.jpg")))
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
      r.errors must not(beEmpty)
      errorCode(r) must_== "UNAUTHENTICATED"
    }

    "rate an image" in {
      SharedTestDb.truncateAll()
      val c   = ContestJuryJdbc.create(None, "WLM", 2024, "UA", None, None, None, None)
      val u   = User.create(User("Jury", "jury@test.com", roles = Set("jury"), contestId = Some(c.getId)))
      val rnd = Round.create(Round(None, 1, None, c.getId, distribution = 0, hasCriteria = false))
      ImageJdbc.batchInsert(Seq(Image(pageId = 202L, title = "File:A.jpg")))
      val r = execute(
        s"""mutation { rateImage(roundId:"${rnd.getId}", pageId:"202", rate:1) { rate roundId } }""",
        authedCtx(u)
      )
      r.errors must beEmpty
      int(field(dataField(r, "rateImage"), "rate")) must_== 1
    }
  }
}
