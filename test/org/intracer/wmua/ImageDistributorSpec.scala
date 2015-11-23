package org.intracer.wmua

import db.scalikejdbc.{ImageJdbc, RoundJdbc, SelectionJdbc, UserJdbc}
import db.{ImageDao, RoundDao, SelectionDao, UserDao}
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

  val (contest1, contest2) = (10, 20)

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
    val dbJurors = jurors.map(userDao.create)

    val preJurors = (1 to jurorsNum).map(i => contestUser(contest, "prejury"))
    preJurors.foreach(userDao.create)

    val orgCom = contestUser(contest, "organizer")
    userDao.create(orgCom)

    val otherContestJurors = (1 to jurorsNum).map(i => contestUser(20, "jury"))
    otherContestJurors.foreach(userDao.create)

    dbJurors
  }

  def createImages(number: Int, contest1: Long, contest2: Long) = {
    val images1 = (101 to 100 + number).map(id => contestImage(id, contest1))
    val images2 = (100 + number + 1 to 100 + number * 2).map(id => contestImage(id, contest2))

    imageDao.batchInsert(images1 ++ images2)

    images1
  }

  "ImageDistributor" should {
    "create first round 1 juror to image" in {
      inMemDbApp {
        val distribution = 1

        val images = createImages(9, contest1, contest2)

        val round = Round(None, 1, Some("Round 1"), contest1, Set("jury"), distribution, Round.ratesById(10), active = true)
        val dbRound = roundDao.create(round)

        val dbJurors = createJurors(contest1, 3)
        val juryIds = dbJurors.map(_.id.get)

        ImageDistributor.distributeImages(contest1, dbRound)

        val selection = selectionDao.findAll()

        selection.size === 9

        selection.map(_.round).toSet === Set(dbRound.id.get)
        selection.map(_.rate).toSet === Set(0)
        selection.map(_.pageId) === images.map(_.pageId)
        selection.map(_.juryId) === juryIds ++ juryIds ++ juryIds
      }
    }

    "create first round 2 jurors to image" in {
      inMemDbApp {
        val distribution = 2
        val numImages = 9
        val numJurors = 3
        val images = createImages(numImages, contest1, contest2)

        val round = Round(None, 1, Some("Round 1"), contest1, Set("jury"), distribution, Round.ratesById(10), active = true)
        val dbRound = roundDao.create(round)

        val dbJurors = createJurors(contest1, numJurors)
        val juryIds = dbJurors.map(_.id.get)

        ImageDistributor.distributeImages(contest1, dbRound)

        val selection = selectionDao.findAll()

        selection.size === numImages * distribution

        selection.map(_.round).toSet === Set(dbRound.id.get)
        selection.map(_.rate).toSet === Set(0)

        val jurorsPerImage = selection.groupBy(_.pageId).values.map(_.size)
        jurorsPerImage.size === numImages
        jurorsPerImage.toSet === Set(2)

        val imagesPerJuror = selection.groupBy(_.juryId).values.map(_.size)
        imagesPerJuror.size === numJurors
        imagesPerJuror.toSet === Set(distribution * numImages / numJurors)
      }
    }
  }
}
