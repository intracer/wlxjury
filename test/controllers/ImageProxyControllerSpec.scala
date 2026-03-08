package controllers

import db.scalikejdbc.TestDb
import org.specs2.mutable.Specification
import play.api.test.Helpers._
import play.api.test.FakeRequest

class ImageProxyControllerSpec extends Specification with TestDb {

  sequential

  "ImageProxyController" should {

    "return 404 for a path not matching the thumb pattern" in {
      testDbApp { implicit app =>
        val result = route(app,
          FakeRequest("GET", "/wikipedia/commons/thumb/bad")).get
        status(result) must beOneOf(NOT_FOUND, BAD_REQUEST)
      }
    }

    "redirect to Wikimedia when image is not in DB" in {
      testDbApp { implicit app =>
        val result = route(app,
          FakeRequest("GET",
            "/wikipedia/commons/thumb/x/xx/NoSuchFile.jpg/250px-NoSuchFile.jpg")).get
        status(result) mustEqual SEE_OTHER
        header("Location", result) must beSome(contain("upload.wikimedia.org"))
      }
    }
  }
}
