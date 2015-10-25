package org.intracer.wmua

import db.{RoundDao, UserDao, SelectionDao, ImageDao}
import db.scalikejdbc.{RoundJdbc, UserJdbc, SelectionJdbc, ImageJdbc}
import org.joda.time.DateTime
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.test.Helpers._

class ImageDistributorSpec extends Specification {

  sequential

  val imageDao: ImageDao = ImageJdbc
  val userDao: UserDao = UserJdbc
  val roundDao: RoundDao = RoundJdbc
  val selectionDao: SelectionDao = SelectionJdbc

  def inMemDbApp[T](block: => T): T = {
    running(FakeApplication(additionalConfiguration = inMemoryDatabase()))(block)
  }

  def contestImage(id: Long, contest: Long) =
    Image(id, contest, s"File:Image$id.jpg", s"url$id", s"pageUrl$id", 640, 480, Some(s"12-345-$id"))

  def contestUser(contest: Long, role: String) =
    User("fullname", "email", None, Set(role), Some("password hash"), contest, Some("en"))

  def createJurors(
                   contest: Long,
                   jurorsNum: Int,
                   preJurors: Boolean = true,
                   orgCom: Boolean = true,
                   otherContest: Option[Long] = Some(20)) = {

    val jurors = (1 to jurorsNum).map(i => contestUser(contest, "jury"))
    val dbJurors = jurors.map(userDao.create).map(u => u.copy(roles = u.roles + s"USER_ID_${u.id.get}"))

    val preJurors = (1 to jurorsNum).map(i => contestUser(contest, "prejury"))
    preJurors.foreach(userDao.create)

    val orgCom = contestUser(contest, "organizer")
    userDao.create(orgCom)

    val otherContestJurors = (1 to jurorsNum).map(i => contestUser(20, "jury"))
    otherContestJurors.foreach(userDao.create)

    dbJurors
  }

  "ImageDistributorTest" should {
    "create first round 1 juror to image" in {
      inMemDbApp {

        val (contest1, contest2) = (10, 20)

        val images1 = (11 to 19).map(id => contestImage(id, contest1))
        val images2 = (21 to 29).map(id => contestImage(id, contest2))

        imageDao.batchInsert(images1 ++ images2)

        val round = Round(None, 1, Some("Round 1"), 10, Set("jury"), 1, Round.ratesById(10), active = true)
        val dbRound = roundDao.create(round)

        val dbJurors = createJurors(contest1, 3)
        val juryIds = dbJurors.map(_.id.get)

        ImageDistributor.distributeImages(contest1, dbRound)

        val selection = selectionDao.findAll()

        selection.size === 9

        selection.map(_.round).toSet === Set(dbRound.id.get)
        selection.map(_.rate).toSet === Set(0)
        selection.map(_.pageId) === images1.map(_.pageId)
        selection.map(_.juryId) === juryIds ++ juryIds ++ juryIds
      }
    }

    "distributeImages" in {
      ok
    }
  }
}
