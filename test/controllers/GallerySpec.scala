package controllers

import db._
import db.scalikejdbc._
import org.intracer.wmua._
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.test.Helpers._

class GallerySpec extends Specification {

  sequential

  val contestDao: ContestJuryDao = ContestJuryJdbc
  val roundDao: RoundDao = RoundJdbc
  val userDao: UserDao = UserJdbc
  val imageDao: ImageDao = ImageJdbc
  val selectionDao: SelectionDao = SelectionJdbc

  var contest: ContestJury = _
  var round: Round = _
  var user: User = _

  def inMemDbApp[T](block: => T): T = {
    running(FakeApplication(additionalConfiguration = inMemoryDatabase()))(block)
  }

  def contestImage(id: Long, contest: Long) =
    Image(id, contest, s"File:Image$id.jpg", s"url$id", s"pageUrl$id", 640, 480, Some(s"12-345-$id"))

  def setUp(rates: Rates = Round.binaryRound) = {
    contest = contestDao.create(None, "WLE", 2015, "Ukraine", None, None, None)
    round = roundDao.create(
      Round(None, 1, contest = contest.id.get, rates = rates, active = true)
    )
    user = userDao.create(
      User("fullname", "email", None, Set("jury"), contest = contest.id)
    )
  }

  def createImages(number: Int, contestId: Long = contest.id.get, startId: Int = 0) = {
    val images = (1 + startId to number + startId).map(id => contestImage(id, contestId))
    imageDao.batchInsert(images)
    images
  }

  def createSelection(images: Seq[Image],
                      rate: Int = 0,
                      user: User = user,
                      round: Round = round) = {
    val selections = images.zipWithIndex.map { case(image, i) =>
      Selection(0, image.pageId, rate, user.id.get, round.id.get)
    }
    selectionDao.batchInsert(selections)
    selections
  }

  "juror" should {
    "see assigned images in binary round" in {
      inMemDbApp {
        /// prepare
        setUp(rates = Round.binaryRound)
        val images = createImages(6)
        createSelection(images.slice(0, 3), rate = 0)

        /// test
        val result = Gallery.getSortedImages("gallery", user.id.get, None, round)

        /// check
        result.size === 3
        result.map(_.image) === images.slice(0, 3)
        result.map(_.selection.size) === Seq(1, 1, 1)
        result.map(_.rate) === Seq(0, 0, 0)
      }
    }

    "see images filtered by rate in binary round" in {
      inMemDbApp {
        /// prepare
        setUp(rates = Round.binaryRound)
        val images = createImages(6)

        def range(rate: Int) = Seq(2 + rate * 2, 4 + rate * 2)

        def slice(rate: Int) = images.slice(range(rate).head, range(rate).last)

        for (rate <- -1 to 1) {
          createSelection(slice(rate), rate = rate)
        }

        for (rate <- -1 to 1) yield {
          /// test
          val result = Gallery.getSortedImages("gallery", user.id.get, Some(rate), round)

          /// check
          result.size === 2
          result.map(_.image) === slice(rate)
          result.map(_.selection.size) === Seq(1, 1)
          result.map(_.rate) === Seq(rate, rate)
        }
      }
    }

    // TODO fix
//    "see images ordered by rate in binary round" in {
//      inMemDbApp {
//        /// prepare
//        setUp(rates = Round.binaryRound)
//        val images = createImages(6)
//
//        def range(rate: Int) = Seq(2 + rate * 2, 4 + rate * 2)
//
//        def slice(rate: Int) = images.slice(range(rate).head, range(rate).last)
//
//        for (rate <- -1 to 1) {
//          createSelection(slice(rate), rate = rate)
//        }
//
//        /// test
//        val result = Gallery.getSortedImages("gallery", user.id.get, None, round)
//
//        /// check
//        result.size === 6
//        result.map(_.image) === slice(1) ++ slice(0) ++ slice(-1)
//        result.map(_.selection.size) === Seq.fill(6)(1)
//        result.map(_.rate) === Seq(1, 1, 0, 0, -1, -1)
//      }
//    }

    "see images ordered by rate in rated round" in {
      inMemDbApp {
        /// prepare
        setUp(rates = Round.ratesById(10))
        val images = createImages(6)


        val selections = images.zipWithIndex.map { case(image, i) =>
          Selection(0, image.pageId, rate = i, user.id.get, round.id.get)
        }
        selectionDao.batchInsert(selections)

        /// test
        val result = Gallery.getSortedImages("gallery", user.id.get, None, round)

        /// check
        result.size === 6
        result.map(_.image) === images.reverse
        result.map(_.selection.size) === Seq.fill(6)(1)
        result.map(_.rate) === (0 to 5).reverse
      }
    }
  }
}
