package controllers

import akka.stream.Materializer
import db.scalikejdbc.{TestDb, User}
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
        val contestsController =
          new ContestController(bot, Helpers.stubControllerComponents())

        userDao.create(
          User("fullname", email, None, Set("root"), contestId = None)
        )
        FakeRequest("POST", "/")
        val request = FakeRequest("POST", "/contests/import")
          .withSession(Secured.UserName -> email)
          .withFormUrlEncodedBody(
            "source" -> "Category:Wiki Loves Earth 2013 in Ukraine")
          .withCSRFToken

        val result = contestsController.importContests().apply(request)
        status(result) mustEqual SEE_OTHER

        val contests = contestDao.findAll()
        contests === List(
          ContestJury(
            Some(1),
            "Wiki Loves Earth",
            2013,
            "Ukraine",
            Some("Category:Images from Wiki Loves Earth 2013 in Ukraine"),
            monumentIdTemplate = Some("UkrainianNaturalHeritageSite")
          ))
      }
    }
  }
}
