package controllers

import db.scalikejdbc.{TestDb, User}
import org.apache.pekko.stream.Materializer
import org.intracer.wmua.ContestJury
import org.scalawiki.MwBot
import play.api.test.CSRFTokenHelper._
import play.api.test.{FakeRequest, Helpers, PlaySpecification}

class ContestSpec extends PlaySpecification with TestDb {

  val email = "email@1.com"

  "import contests" should {
    "import Ukraine" in {
      testDbApp { app =>
        implicit val materializer: Materializer = app.materializer

        val bot = MwBot.fromHost("commons.wikimedia.org")
        val contestsController = new ContestController(bot, Helpers.stubControllerComponents())

        val created = userDao.create(
          User(
            fullname = "fullname",
            email = email,
            id = None,
            roles = Set("root"),
            contestId = None
          )
        )
        val request = FakeRequest("POST", "/contests/import")
          .withSession(Secured.UserName -> email)
          .withFormUrlEncodedBody("source" -> "Category:Wiki Loves Earth 2013 in Ukraine")
          .withCSRFToken

        val result = call(contestsController.importContests(), request)
        status(result) mustEqual SEE_OTHER

        val contests = contestDao.findAll()
        contests === List(
          ContestJury(
            id = Some(1),
            name = "Wiki Loves Earth",
            year = 2013,
            country = "Ukraine",
            images = Some("Category:Images from Wiki Loves Earth 2013 in Ukraine"),
            monumentIdTemplate = Some("UkrainianNaturalHeritageSite")
          )
        )
      }
    }
  }
}
