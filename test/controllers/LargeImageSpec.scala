package controllers

import db.scalikejdbc._
import org.intracer.wmua._
import play.api.libs.json.Json
import play.api.mvc.Security
import play.api.test.CSRFTokenHelper._
import play.api.test.{FakeRequest, PlaySpecification}

class LargeImageSpec extends PlaySpecification with TestDb {

  sequential

  var contest: ContestJury = _
  var round: Round = _
  var user: User = _
  val email = "email@1.com"

  def contestImage(id: Long, contestId: Long) =
    Image(id, s"File:Image$id.jpg", None, None, 640, 480, Some(s"12-345-$id"))

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

  def request(url: String) = {
    FakeRequest(GET, url)
      .withSession(Security.username -> email)
      .withHeaders("Accept" -> "application/json")
      .withCSRFToken
  }

  def imageJson(id: Int, rate: Int = 0) = {
    s"""{"image":{"pageId":$id,"title":"File:Image$id.jpg","width":640,"height":480,"monumentId":"12-345-$id"},
       |"selection":[{"pageId":$id,"juryId":1,"roundId":1,"rate":$rate,"id":${id + 1}}],
       |"countFromDb":0}""".stripMargin
  }

  def mkJson(elems: String*) = Json.parse(elems.mkString("[", ",", "]"))

  "get 1 unrated image" in {
    testDbApp { app =>
      implicit val materializer = app.materializer

      setUp(rates = Round.ratesById(5))

      val images = createImages(1)
      createSelection(images)

      val result = LargeView.large(user.id.get, images.head.pageId, roundId = round.id.get, rate = Some(0), module = "gallery")
        .apply(request(s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.head.pageId}?rate=0"))

      status(result) mustEqual OK
      contentAsJson(result) mustEqual mkJson(imageJson(0))
    }
  }

  "get 1 rated image" in {
    testDbApp { app =>
      implicit val materializer = app.materializer

      setUp(rates = Round.ratesById(5))

      val images = createImages(1)
      createSelection(images, rate = 5)

      val result = LargeView.large(user.id.get, images.head.pageId, roundId = round.id.get, rate = None, module = "byrate")
        .apply(request(s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.head.pageId}?module=byrate"))

      status(result) mustEqual OK
      contentAsJson(result) mustEqual mkJson(imageJson(0, rate = 5))
    }
  }

  "get 2 unrated images" in {
    testDbApp { app =>
      implicit val materializer = app.materializer

      setUp(rates = Round.ratesById(5))

      val images = createImages(2)
      createSelection(images)

      val result = LargeView.large(user.id.get, images.head.pageId, roundId = round.id.get, rate = None, module = "gallery")
        .apply(request(s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.head.pageId}?rate=0"))
        .run()

      status(result) mustEqual OK
      contentAsJson(result) mustEqual mkJson(imageJson(0), imageJson(1))
    }
  }

  "get 2 rated images" in {
    testDbApp { app =>
      implicit val materializer = app.materializer

      setUp(rates = Round.ratesById(5))

      val images = createImages(2)
      createSelection(images.init, 3)
      createSelection(images.tail, 5)

      val result = LargeView.large(user.id.get, images.last.pageId, roundId = round.id.get, rate = None, module = "byrate")
        .apply(request(s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.last.pageId}?module=byrate"))
        .run()

      status(result) mustEqual OK
      contentAsJson(result) mustEqual mkJson(imageJson(1, rate = 5), imageJson(0, rate = 3))
    }
  }

  "get first unrated image from 2" in {
    testDbApp { app =>
      implicit val materializer = app.materializer

      setUp(rates = Round.ratesById(5))

      val images = createImages(2)
      createSelection(images.init, 0)
      createSelection(images.tail, 5)

      val result = LargeView.large(user.id.get, images.head.pageId, roundId = round.id.get, rate = Some(0), module = "gallery")
        .apply(request(s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.head.pageId}?rate=0"))
        .run()

      status(result) mustEqual OK
      contentAsJson(result) mustEqual mkJson(imageJson(0))
    }
  }

  "get last unrated image from 2" in {
    testDbApp { app =>
      implicit val materializer = app.materializer

      setUp(rates = Round.ratesById(5))

      val images = createImages(2)
      createSelection(images.init, 5)
      createSelection(images.tail, 0)

      val result = LargeView.large(user.id.get, images.last.pageId, roundId = round.id.get, rate = Some(0), module = "gallery")
        .apply(request(s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.last.pageId}?rate=0"))
        .run()

      status(result) mustEqual OK
      contentAsJson(result) mustEqual mkJson(imageJson(1))
    }
  }

  "get first rated image from 2" in {
    testDbApp { app =>
      implicit val materializer = app.materializer

      setUp(rates = Round.ratesById(5))

      val images = createImages(2)
      createSelection(images.init, 5)
      createSelection(images.tail, 0)

      val result = LargeView.large(user.id.get, images.head.pageId, roundId = round.id.get, rate = None, module = "byrate")
        .apply(request(s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.head.pageId}?module=byrate"))
        .run()

      status(result) mustEqual OK
      contentAsJson(result) mustEqual mkJson(imageJson(0, rate = 5), imageJson(1, rate = 0))
    }
  }

  "get last rated image from 2" in {
    testDbApp { app =>
      implicit val materializer = app.materializer

      setUp(rates = Round.ratesById(5))

      val images = createImages(2)
      createSelection(images.init, 0)
      createSelection(images.tail, 5)

      val result = LargeView.large(user.id.get, images.last.pageId, roundId = round.id.get, rate = None, module = "byrate")
        .apply(request(s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.last.pageId}?module=byrate"))
        .run()

      status(result) mustEqual OK
      contentAsJson(result) mustEqual mkJson(imageJson(1, rate = 5), imageJson(0, rate = 0))
    }
  }

  "rate 1 unrated image" in {
    testDbApp { app =>
      implicit val materializer = app.materializer

      setUp(rates = Round.ratesById(5))

      val images = createImages(1)
      createSelection(images)

      val result = LargeView.rateByPageId(round.id.get, images.head.pageId, select = 5, module = "gallery", rate = Some(0))
        .apply(request(s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.head.pageId}/select/5?rate=0"))
        .run()

      status(result) mustEqual SEE_OTHER
      redirectLocation(result) must beSome.which(_ === s"/gallery/round/${round.id.get}/user/${user.id.get}/page/1?rate=0")
    }
  }

  "unrate 1 rated image" in {
    testDbApp { app =>
      implicit val materializer = app.materializer

      setUp(rates = Round.ratesById(5))

      val images = createImages(1)
      createSelection(images, rate = 5)

      val result = LargeView.rateByPageId(round.id.get, images.head.pageId, select = 0, module = "gallery", rate = None)
        .apply(request(s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.head.pageId}/select/0?module=byrate"))
        .run()

      status(result) mustEqual SEE_OTHER
      redirectLocation(result) must beSome.which(_ === s"/gallery/round/${round.id.get}/user/${user.id.get}/page/1")
    }
  }

  "rerate 1 rated image" in {
    testDbApp { app =>
      implicit val materializer = app.materializer

      setUp(rates = Round.ratesById(5))

      val images = createImages(1)
      createSelection(images, rate = 5)

      val result = LargeView.rateByPageId(round.id.get, images.head.pageId, select = 3, module = "byrate", rate = None)
        .apply(request(s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.head.pageId}/select/0?module=byrate"))
        .run()

      status(result) mustEqual SEE_OTHER
      redirectLocation(result) must beSome.which(_ === s"/byrate/round/${round.id.get}/user/${user.id.get}/page/1")
    }
  }

  "rerate 1st rated image from two rated" in {
    testDbApp { app =>
      implicit val materializer = app.materializer

      setUp(rates = Round.ratesById(5))

      val images = createImages(2)
      createSelection(images.init, rate = 5)
      createSelection(images.tail, rate = 3)

      val result = LargeView.rateByPageId(round.id.get, images.head.pageId, select = 2, module = "byrate", rate = None)
        .apply(request(s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.head.pageId}/select/0?module=byrate"))
        .run()

      status(result) mustEqual SEE_OTHER
      redirectLocation(result) must beSome.which(_ === s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.last.pageId}?module=byrate")
    }
  }

  "rerate 2nd rated image from two rated" in {
    testDbApp { app =>
      implicit val materializer = app.materializer

      setUp(rates = Round.ratesById(5))

      val images = createImages(2)
      createSelection(images.init, rate = 3)
      createSelection(images.tail, rate = 2)

      val result = LargeView.rateByPageId(round.id.get, images.last.pageId, select = 4, module = "byrate", rate = None)
        .apply(request(s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.last.pageId}/select/0?module=byrate"))
        .run()

      status(result) mustEqual SEE_OTHER
      redirectLocation(result) must beSome.which(_ === s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.head.pageId}?module=byrate")
    }
  }

  "rate 1st unrated image from two" in {
    testDbApp { app =>
      implicit val materializer = app.materializer

      setUp(rates = Round.ratesById(5))

      val images = createImages(2)
      createSelection(images.init, 0)
      createSelection(images.tail, 5)

      val result = LargeView.rateByPageId(round.id.get, images.head.pageId, select = 5, module = "gallery", rate = Some(0))
        .apply(request(s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.head.pageId}/select/5?rate=0"))
        .run()

      status(result) mustEqual SEE_OTHER
      redirectLocation(result) must beSome.which(_ === s"/gallery/round/${round.id.get}/user/${user.id.get}/page/1?rate=0")
    }
  }

  "rate 2nd unrated image from two" in {
    testDbApp { app =>
      implicit val materializer = app.materializer

      setUp(rates = Round.ratesById(5))

      val images = createImages(2)
      createSelection(images.init, 5)
      createSelection(images.tail, 0)

      val result = LargeView.rateByPageId(round.id.get, images.last.pageId, select = 5, module = "gallery", rate = Some(0))
        .apply(request(s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.last.pageId}/select/5?rate=0"))
        .run()

      status(result) mustEqual SEE_OTHER
      redirectLocation(result) must beSome.which(_ === s"/gallery/round/${round.id.get}/user/${user.id.get}/page/1?rate=0")
    }
  }

  "rate 1st unrated image from two unrated" in {
    testDbApp { app =>
      implicit val materializer = app.materializer

      setUp(rates = Round.ratesById(5))

      val images = createImages(2)
      createSelection(images)

      val result = LargeView.rateByPageId(round.id.get, images.head.pageId, select = 5, module = "gallery", rate = Some(0))
        .apply(request(s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.head.pageId}/select/5?rate=0"))
        .run()

      status(result) mustEqual SEE_OTHER
      redirectLocation(result) must beSome.which(_ === s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.last.pageId}?rate=0")
    }
  }

  "rate 2nd unrated image from two unrated" in {
    testDbApp { app =>
      implicit val materializer = app.materializer

      setUp(rates = Round.ratesById(5))

      val images = createImages(2)
      createSelection(images)

      val result = LargeView.rateByPageId(round.id.get, images.last.pageId, select = 5, module = "gallery", rate = Some(0))
        .apply(request(s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.last.pageId}/select/5?rate=0"))
        .run()

      status(result) mustEqual SEE_OTHER
      redirectLocation(result) must beSome.which(_ === s"/large/round/${round.id.get}/user/${user.id.get}/pageid/${images.head.pageId}?rate=0")
    }
  }

  "rate unrated image from the middle of many" in {
    testDbApp { app =>
      implicit val materializer = app.materializer

      setUp(rates = Round.ratesById(5))

      val images = createImages(15)
      createSelection(images)

      val pageId = images(7).pageId
      val nextPageId = images(8).pageId

      val result = LargeView.rateByPageId(round.id.get, pageId, select = 5, module = "gallery", rate = Some(0))
        .apply(request(s"/large/round/${round.id.get}/user/${user.id.get}/pageid/$pageId/select/5?rate=0"))
        .run()

      status(result) mustEqual SEE_OTHER
      redirectLocation(result) must beSome.which(_ === s"/large/round/${round.id.get}/user/${user.id.get}/pageid/$nextPageId?rate=0")
    }
  }
}