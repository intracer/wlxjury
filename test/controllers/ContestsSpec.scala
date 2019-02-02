package controllers

import db.scalikejdbc.{ContestJuryJdbc, InMemDb, UserJdbc}
import org.intracer.wmua.{ContestJury, User}
import org.scalawiki.MwBot
import play.api.mvc.Security
import play.api.test.{FakeRequest, PlaySpecification}
import play.api.test.CSRFTokenHelper._

class ContestsSpec extends PlaySpecification with InMemDb {

  val userDao = UserJdbc
  val contestDao = ContestJuryJdbc
  val email = "email@1.com"

  "import contests" should {
    "import Ukraine" in {
      inMemDbApp { app =>
        implicit val materializer = app.materializer

        val bot = MwBot.fromHost("commons.wikimedia.org")
        val contestsController = new Contests(bot)

        val user = userDao.create(
          User("fullname", email, None, Set("root"), contestId = None)
        )
        FakeRequest("POST", "/")
        val request = FakeRequest("POST", "/contests/import")
          .withSession(Security.username -> email)
          .withFormUrlEncodedBody("source" -> "Category:Wiki Loves Earth 2013 in Ukraine")
          .withCSRFToken

        val result = call(contestsController.importContests, request)
        status(result) mustEqual SEE_OTHER

        val contests = contestDao.findAll()
        contests === List(ContestJury(Some(1), "Wiki Loves Earth", 2013, "Ukraine",
          Some("Category:Images from Wiki Loves Earth 2013 in Ukraine"),
          monumentIdTemplate = Some("UkrainianNaturalHeritageSite")
        ))
      }
    }
  }
}
