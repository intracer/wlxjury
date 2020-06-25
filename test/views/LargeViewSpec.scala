package views

import db.scalikejdbc.{Round, User}
import org.intracer.wmua._
import play.api.test.{Helpers, PlaySpecification}
import play.twirl.api.Html

class LargeViewSpec extends PlaySpecification {

  import play.api.i18n._
  implicit val lang = Lang("en-US")

  "large view" should {
    "have correct rating link" in {
      implicit val request = play.api.test.FakeRequest("GET", "/")
      implicit val messages = Helpers.stubMessagesApi().preferred(request)

      val files = Seq(
        ImageWithRating(
          Image(pageId = 1, title = "File:1.jpg"),
          selection = Nil)
      )

      val html = views.html.large.main_large(
        title = "title",
        user = User("name", "email", id = Some(1), roles = Set("jury"), contestId = Some(1)),
        asUserId = 0,
        score = 0.0,
        readOnly = false,
        url = "url",
        files = files,
        index = 0,
        page = 0,
        rate = Some(0),
        region = "all",
        round = Round(id = Some(1), number = 1, contestId = 1, active = true, rates = Round.ratesById(5)),
        module = "byrate")(Html("html"))

      val view = contentAsString(html)
      view must not contain "&amp"
    }
  }

}