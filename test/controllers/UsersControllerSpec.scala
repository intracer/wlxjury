package controllers

import db.scalikejdbc.{Contest, TestDb, User}
import org.specs2.mock.Mockito
import play.api.mvc._
import play.api.test.CSRFTokenHelper._
import play.api.test._

class UsersControllerSpec extends PlaySpecification with Results with TestDb with Mockito {

  sequential

  val usersController = new UsersController(mock[SMTPOrWikiMail])

  "save user" should {

    "fail empty request" in {
      testDbApp { app =>
        implicit val materializer = app.materializer
        val result = usersController.saveUser().apply(FakeRequest().withCSRFToken)
        redirectLocation(result) === Some("")
      }
    }

//    "create new user" in {
//      testDbApp { app =>
//        implicit val materializer = app.materializer
//        val result = usersController.saveUser().apply(FakeRequest().withHeaders(
//          "fullname" -> "name",
//          "email" -> "mail@server"
//        ))
//        status(result) === BAD_REQUEST
//      }
//    }
  }

  "fill template" should {

    "fill contest info" in {
      withDb {
        val sender = User("Admin User", "email@server.com", None, contestId = None)
        val contest = Contest(name = "Wiki Loves Earth", year = 2016, country = "Ukraine", images = None, id = None)
        val template = "Organizing committee of {{ContestType}} {{ContestYear}} {{ContestCountry}} is glad to welcome you as a jury member\n" +
          "Please visit {{JuryToolLink}}\n" +
          "Regards, {{AdminName}}"
        val filled = usersController.fillGreeting(template, contest, sender, sender)
        filled === "Organizing committee of Wiki Loves Earth 2016 Ukraine is glad to welcome you as a jury member\n" +
          "Please visit https://jury.wle.org.ua/\n" +
          "Regards, Admin User"
      }
    }
  }
}
