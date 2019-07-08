package controllers

import com.mohiva.play.silhouette.api.{Environment, LoginInfo}
import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import com.mohiva.play.silhouette.test.FakeEnvironment
import db.scalikejdbc.{InMemDb, UserJdbc}
import org.intracer.wmua.User
import org.specs2.mock.Mockito
import play.api.mvc._
import play.api.test.CSRFTokenHelper._
import play.api.test._

import scala.concurrent.ExecutionContext.Implicits.global

class LoginSpec extends PlaySpecification with Results with InMemDb with Mockito {

  sequential

  val admin = new Admin(mock[SMTPOrWikiMail])
  val user = User("name", "qwerty@dot.com", password = Some(UserJdbc.sha1("strong")))
  val environment: Environment[DefaultEnv] = new FakeEnvironment[DefaultEnv](Seq(LoginInfo("default", user.email) -> user))
  val registry = SocialProviderRegistry(Nil)
  val login = new Login(admin, Helpers.stubControllerComponents(), environment, registry)

  "auth" should {
    "fail empty request" in {
      inMemDb {
        val result = login.auth().apply(FakeRequest().withCSRFToken)
        status(result) === BAD_REQUEST
      }
    }

    "fail when no users" in {
      inMemDb {
        val result = login.auth().apply(FakeRequest().withHeaders(
          "login" -> "qwerty",
          "password" -> "1234"
        ).withCSRFToken)

        status(result) === BAD_REQUEST
      }
    }

    "fail when wrong password" in {
      inMemDb {
        UserJdbc.create(User("name", "qwerty@dot.com"))

        val result = login.auth().apply(FakeRequest().withHeaders(
          "login" -> "qwerty@dot.com",
          "password" -> "1234"
        ).withCSRFToken)

        status(result) === BAD_REQUEST
      }
    }

    "no rights" in {
      inMemDb {
        UserJdbc.create(user)
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
      inMemDb {
        UserJdbc.create(user.copy(roles = Set(User.ROOT_ROLE)))
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
