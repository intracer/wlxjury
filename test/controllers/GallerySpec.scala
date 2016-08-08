package controllers

import db._
import db.scalikejdbc._
import org.intracer.wmua.{Image, Round, Selection, User}
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

  def inMemDbApp[T](block: => T): T = {
    running(FakeApplication(additionalConfiguration = inMemoryDatabase()))(block)
  }

  def contestImage(id: Long, contest: Long) =
    Image(id, contest, s"File:Image$id.jpg", s"url$id", s"pageUrl$id", 640, 480, Some(s"12-345-$id"))


  "juror" should {
    "see assigned images in binary round" in {
      inMemDbApp {

        /// prepare

        val contest = contestDao.create(None, "WLE", 2015, "Ukraine", None, None, None)
        val round = roundDao.create(
          Round(None, 1, Some("Round 1"), contest.id.get, Set("jury"), 3, Round.ratesById(10), active = true)
        )
        val images = (1 to 10).map(id => contestImage(id, contest.id.get))
        imageDao.batchInsert(images)

        val user = userDao.create(
          User("fullname", "email", None, Set("jury"), Some("password hash"), contest.id)
        )

        val selections = images.slice(0, 3).map { image =>
          Selection(0, image.pageId, 0, user.id.get, round.id.get)
        }
        selectionDao.batchInsert(selections)

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
