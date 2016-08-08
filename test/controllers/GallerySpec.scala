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

  def setUp() = {
    contest = contestDao.create(None, "WLE", 2015, "Ukraine", None, None, None)
    round = roundDao.create(
      Round(None, 1, contest = contest.id.get, rates = Round.binaryRound, active = true)
    )
    user = userDao.create(
      User("fullname", "email", None, Set("jury"), contest = contest.id)
    )
  }

  def createImages(number: Int, contest: Long = contest.id.get, startId: Int = 0) = {
    val images = (1 + startId to number + startId).map(id => contestImage(id, contest))
    imageDao.batchInsert(images)
    images
  }

  def createSelection(images: Seq[Image],
                      rate: Int = 0,
                      user: User = user,
                      round: Round = round,
                      startId: Int = 0) = {
    val selections = images.map { image =>
      Selection(0, image.pageId, rate, user.id.get, round.id.get)
    }
    selectionDao.batchInsert(selections)
    selections
  }

  "juror" should {
    "see assigned images in binary round" in {
      inMemDbApp {
        /// prepare
        setUp()
        val images = createImages(6)
        createSelection(images.slice(0, 3))

        /// test
        val result = Gallery.getSortedImages("gallery", user.id.get, None, user, round)

        /// check
        result.size === 3
        result.map(_.image) === images.slice(0, 3)
        result.map(_.selection.size) === Seq(1, 1, 1)
        result.map(_.rate) === Seq(0, 0, 0)
      }
    }
  }

}
