package api

import controllers.ContestController
import org.apache.pekko.http.scaladsl.testkit.Specs2RouteTest
import org.intracer.wmua.ContestJury
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

class ApiSpec
  extends Specification
  with Mockito
  with Specs2RouteTest
  with JsonFormat {

  val controller = mock[ContestController]
  val route = new Api(controller).routes
  val contest = ContestJury(
    id = Some(1),
    name = "WLM",
    year = 2022,
    country = "Ukraine",
    images = Some("Category:Images_from_Wiki_Loves_Monuments_2022_in_Ukraine")
  )
  "api" should {
    "get contests" in {
      controller.findContests returns List(contest)
      Get("api/contests") ~> route ~> check {
        responseAs[List[ContestJury]] === List(contest)
      }
    }
  }

}
