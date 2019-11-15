package controllers

import db.scalikejdbc.{TestDb, UserJdbc}
import org.intracer.wmua.User
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.mvc.{RequestHeader, Security, Session}

class SecuredSpec extends Specification with Mockito with TestDb {

  sequential

  val userDao = UserJdbc

  def mockRequest(username: String): RequestHeader = {
    val request = mock[RequestHeader]
    val session = mock[Session]
    session.get(Security.username) returns Some(username)
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
        new Secured {}.userFromRequest(request) === Some(created)
      }
    }

    "be None if not in db" in {
      withDb {
        val username = "user login"
        val user = User("fullname", username, None, Set("jury"), Some("password hash"), Some(10))

        val created = userDao.create(user)

        val request: RequestHeader = mockRequest(username + " other")
        new Secured {}.userFromRequest(request) === None
      }
    }

  }

}
