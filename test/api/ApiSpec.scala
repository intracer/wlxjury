package api

import akka.http.scaladsl.testkit.Specs2RouteTest
import controllers.ContestsController
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class ApiSpec extends Specification with Mockito with Specs2RouteTest {
  val controller = mock[ContestsController]
  val route = new Api(controller).routes
  "api" should {
    "get contests" in {
      controller.findContests returns Nil
      Get("api/contests") ~> route ~> check {
        responseAs[String] === "[]"
      }
    }
  }

}
