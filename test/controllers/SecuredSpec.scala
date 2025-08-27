package controllers

import db.scalikejdbc.{TestDb, User}
import org.specs2.mock.Mockito
import play.api.mvc.{RequestHeader, Session}
import play.api.test.{Helpers, PlaySpecification}

class SecuredWithCc extends Secured(Helpers.stubControllerComponents())

class SecuredSpec extends PlaySpecification with Mockito with TestDb {

  sequential

  def mockRequest(username: String): RequestHeader = {
    val request = mock[RequestHeader]
    val session = mock[Session]
    session.get(Secured.UserName) returns Some(username)
    request.session returns session
    request
  }

  "user" should {
    "load from db" in {
      withDb {
        val username = "user@server.com"
        val user = User("fullname", username, None, Set("jury"), Some("password hash"), Some(10), createdAt = Some(now))

        val created = userDao.create(user)

        val request: RequestHeader = mockRequest(username)
        new SecuredWithCc().userFromRequest(request) === Some(created)
      }
    }

    "be None if not in db" in {
      withDb {
        val username = "user login"
        val user = User("fullname", username, None, Set("jury"), Some("password hash"), Some(10))

        val created = userDao.create(user)

        val request: RequestHeader = mockRequest(username + " other")
        new SecuredWithCc().userFromRequest(request) === None
      }
    }

  }

}
