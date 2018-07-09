package controllers

import db.scalikejdbc._
import org.intracer.wmua._
import play.api.libs.json.Json
import play.api.mvc.Security
import play.api.test.CSRFTokenHelper._
import play.api.test.{FakeRequest, PlaySpecification}

class LargeImageSpec extends PlaySpecification with InMemDb {

  sequential

  val contestDao = ContestJuryJdbc
  val roundDao = RoundJdbc
  val userDao = UserJdbc
  val imageDao = ImageJdbc
  val selectionDao = SelectionJdbc

  var contest: ContestJury = _
  var round: Round = _
  var user: User = _
  val email = "email@1.com"

  def contestImage(id: Long, contestId: Long) =
    Image(id, s"File:Image$id.jpg", None, None, 640, 480, Some(s"12-345-$id"))

  def contestUser(i: Int, contestId: Long = contest.getId, role: String = "jury") =
    User("fullname" + i, "email" + i, None, Set(role), contestId = Some(contestId))

  def setUp(rates: Rates = Round.binaryRound) = {
    contest = contestDao.create(None, "WLE", 2015, "Ukraine")
    round = roundDao.create(
      Round(None, 1, contestId = contest.getId, rates = rates, active = true)
    )
    user = userDao.create(
      User("fullname", email, None, Set("jury"), contestId = contest.id)
    )
  }

  def createImages(number: Int, contestId: Long = contest.getId, startId: Int = 0) = {
    val images = (startId until number + startId).map(id => contestImage(id, contestId))
    imageDao.batchInsert(images)
    images
  }

  def createSelection(images: Seq[Image],
                      rate: Int = 0,
                      user: User = user,
                      round: Round = round) = {
    val selections = images.zipWithIndex.map { case (image, i) =>
      Selection(image, user, round, rate)
    }
    selectionDao.batchInsert(selections)
    selections
  }

  "get 1 image" in {
    inMemDbApp { app =>
      implicit val materializer = app.materializer

      setUp(rates = Round.ratesById(5))

      val images = createImages(1)
      val selection = createSelection(images)

      val request = FakeRequest(GET, s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.head.pageId}")
        .withSession(Security.username -> email)
        .withHeaders("Accept" -> "application/json")
        .withCSRFToken

      val result = Gallery.large(user.id.get, images.head.pageId, roundId = round.id.get, rate = None, module = "byrate").apply(request)

      status(result) mustEqual OK
      contentAsJson(result) mustEqual
        Json.parse("""[{"image":{"pageId":0,"title":"File:Image0.jpg","width":640,"height":480,"monumentId":"12-345-0"},
          |"selection":[{"pageId":0,"juryId":1,"roundId":1,"rate":0,"id":1}],
          |"countFromDb":0}]""".stripMargin)
    }
  }

  "rate 1 image" in {
    inMemDbApp { app =>
      implicit val materializer = app.materializer

      setUp(rates = Round.ratesById(5))

      val images = createImages(1)
      val selection = createSelection(images)

      val request = FakeRequest(GET, s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.head.pageId}/select/2?module=byrate")
        .withSession(Security.username -> email)
        .withHeaders("Accept" -> "application/json")
        .withCSRFToken

      val result = Gallery.selectByPageId(round.id.get, images.head.pageId, select = 5, module = "byrate").apply(request)

      status(result) mustEqual SEE_OTHER
      redirectLocation(result) must beSome.which(_ == s"/byrate/round/${round.id.get}/user/${user.id.get}/page/1")
    }
  }

}
