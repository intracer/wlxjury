package controllers

import db.scalikejdbc.{TestDb, User}
import org.specs2.mock.Mockito
import play.api.mvc._
import play.api.test.CSRFTokenHelper._
import play.api.test._

class LoginControllerSpec extends PlaySpecification with Results with TestDb with Mockito {

  sequential

  val admin = new UsersController(mock[SMTPOrWikiMail])
  val login = new LoginController(admin)

  "auth" should {
    "fail empty request" in {
      withDb {
        val result = login.auth().apply(FakeRequest().withCSRFToken)
        status(result) === BAD_REQUEST
      }
    }

    "fail when no users" in {
      withDb {
        val result = login.auth().apply(FakeRequest().withHeaders(
          "login" -> "qwerty",
          "password" -> "1234"
        ).withCSRFToken)

        status(result) === BAD_REQUEST
      }
    }

    "fail when wrong password" in {
      withDb {
        User.create(User("name", "qwerty@dot.com"))

        val result = login.auth().apply(FakeRequest().withHeaders(
          "login" -> "qwerty@dot.com",
          "password" -> "1234"
        ).withCSRFToken)

        status(result) === BAD_REQUEST
      }
    }

    "no rights" in {
      withDb {
        User.create(User("name", "qwerty@dot.com", password = Some(User.sha1("strong"))))
        val result = login.auth().apply(FakeRequest().withFormUrlEncodedBody(
          "login" -> "qwerty@dot.com",
          "password" -> "strong"
        ).withCSRFToken)

        status(result) === SEE_OTHER
        header(LOCATION, result) === Some("/error?message=You+don%27t+have+permission+to+access+this+page")
//        val cookie = header(SET_COOKIE, result).get
//        cookie must contain("PLAY_SESSION=")
//        cookie must contain("-username=qwerty%40dot.com;")
      }
    }

    "root rights" in {
      withDb {
        User.create(
          User("name", "qwerty@dot.com",
            password = Some(User.sha1("strong")),
            roles = Set(User.ROOT_ROLE))
        )
        val result = login.auth().apply(FakeRequest().withFormUrlEncodedBody(
          "login" -> "qwerty@dot.com",
          "password" -> "strong"
        ).withCSRFToken)

        status(result) === SEE_OTHER
        header(LOCATION, result)  === Some("/contests")
//        val cookie = header(SET_COOKIE, result).get
//        cookie must contain("PLAY_SESSION=")
//        cookie must contain("-username=qwerty%40dot.com;")
      }
    }

  }
}
