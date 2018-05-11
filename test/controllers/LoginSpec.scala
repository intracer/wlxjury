package controllers

import db.scalikejdbc.{InMemDb, UserJdbc}
import org.intracer.wmua.User
import org.specs2.mock.Mockito
import play.api.mvc._
import play.api.test._
import spray.util.pimpFuture

class LoginSpec extends PlaySpecification with Results with InMemDb with Mockito {

  sequential

  val admin = new Admin(mock[SMTPOrWikiMail])
  val login = new Login(admin)

  "auth" should {
    "fail empty request" in {
      inMemDbApp {
        val result = login.auth().apply(FakeRequest())
        status(result) === BAD_REQUEST
      }
    }

    "fail when no users" in {
      inMemDbApp {
        val result = login.auth().apply(FakeRequest().withHeaders(
          "login" -> "qwerty",
          "password" -> "1234"
        ))

        status(result) === BAD_REQUEST
      }
    }

    "fail when wrong password" in {
      inMemDbApp {
        UserJdbc.create(User("name", "qwerty@dot.com"))

        val result = login.auth().apply(FakeRequest().withHeaders(
          "login" -> "qwerty@dot.com",
          "password" -> "1234"
        ))

        status(result) === BAD_REQUEST
      }
    }

    "no rights" in {
      inMemDbApp {
        UserJdbc.create(User("name", "qwerty@dot.com", password = Some(UserJdbc.sha1("strong"))))
        val result = login.auth().apply(FakeRequest().withFormUrlEncodedBody(
          "login" -> "qwerty@dot.com",
          "password" -> "strong"
        ))

        val r = result.await
        r.header.status === SEE_OTHER
        r.header.headers(LOCATION) === "/error?message=You+don%27t+have+permission+to+access+this+page"
        val cookie = r.header.headers(SET_COOKIE)
        cookie must contain("PLAY_SESSION=")
        cookie must contain("-username=qwerty%40dot.com;")
      }
    }

    "root rights" in {
      inMemDbApp {
        UserJdbc.create(
          User("name", "qwerty@dot.com",
            password = Some(UserJdbc.sha1("strong")),
            roles = Set(User.ROOT_ROLE))
        )
        val result = login.auth().apply(FakeRequest().withFormUrlEncodedBody(
          "login" -> "qwerty@dot.com",
          "password" -> "strong"
        ))

        val r = result.await
        r.header.status === SEE_OTHER
        r.header.headers(LOCATION) === "/contests"
        val cookie = r.header.headers(SET_COOKIE)
        cookie must contain("PLAY_SESSION=")
        cookie must contain("-username=qwerty%40dot.com;")
      }
    }

  }
}
