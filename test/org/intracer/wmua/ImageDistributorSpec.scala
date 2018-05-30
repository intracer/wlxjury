package org.intracer.wmua

import db.scalikejdbc._
import org.specs2.mutable.Specification

class ImageDistributorSpec extends Specification with InMemDb {

  sequential

  val imageDao = ImageJdbc
  val userDao = UserJdbc
  val roundDao = RoundJdbc
  val selectionDao = SelectionJdbc

  val (contest1, contest2) = (10, 20)

  def image(id: Long) =
    Image(id, s"File:Image$id.jpg", None, None, 640, 480, Some(s"12-345-$id"))

  def contestUser(contest: Long, role: String, i: Int) =
    User("fullname" + i, "email" + i, None, Set(role), Some("password hash"), Some(contest), Some("en"))

  def createJurors(
                    contest: Long,
                    jurorsNum: Int,
                    preJurors: Boolean = true,
                    orgCom: Boolean = true,
                    otherContest: Option[Long] = Some(20)) = {

    val jurors = (1 to jurorsNum).map(i => contestUser(contest, "jury", i))
    val dbJurors = jurors.map(userDao.create)

    val preJurors = (1 to jurorsNum).map(i => contestUser(contest, "prejury", i + 100))
    preJurors.foreach(userDao.create)

    val orgCom = contestUser(contest, "organizer", 200)
    userDao.create(orgCom)

    val otherContestJurors = (1 to jurorsNum).map(i => contestUser(20, "jury", i + 300))
    otherContestJurors.foreach(userDao.create)

    dbJurors
  }

  def createContests(contestIds: Long*) = contestIds.foreach {
    id =>
      val contest = ContestJuryJdbc.create(Some(id), "contest" + id, 2000 + id.toInt, "country" + id)
      ContestJuryJdbc.setImagesSource(id, Some("Images from " + contest.name))
  }

  def createImages(number: Int, contest1: Long, contest2: Long) = {
    val images1 = (101 to 100 + number).map(id => image(id))
    val images2 = (100 + number + 1 to 100 + number * 2).map(id => image(id))

    imageDao.batchInsert(images1 ++ images2)

    CategoryLinkJdbc.addToCategory(ContestJuryJdbc.findById(contest1).flatMap(_.categoryId).get, images1)
    CategoryLinkJdbc.addToCategory(ContestJuryJdbc.findById(contest2).flatMap(_.categoryId).get, images2)

    images1
  }

  "ImageDistributor" should {
    "create first round 1 juror to image" in {
      inMemDbApp {
        val distribution = 1

        createContests(contest1, contest2)
        val images = createImages(9, contest1, contest2)

        val round = Round(None, 1, Some("Round 1"), contest1, Set("jury"), distribution, Round.ratesById(10), active = true)
        val dbRound = roundDao.create(round)

        val dbJurors = createJurors(contest1, 3)
        val juryIds = dbJurors.map(_.id.get)

        Tools.distributeImages(dbRound, dbJurors, None)

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

        createContests(contest1, contest2)
        val images = createImages(numImages, contest1, contest2)

        val round = Round(None, 1, Some("Round 1"), contest1, Set("jury"), distribution, Round.ratesById(10), active = true)
        val dbRound = roundDao.create(round)

        val dbJurors = createJurors(contest1, numJurors)
        val juryIds = dbJurors.map(_.id.get)

        Tools.distributeImages(dbRound, dbJurors, None)

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

    "create second round 1 juror to image in the first" in {
      inMemDbApp {
        val distribution = 1

        createContests(contest1, contest2)
        val images = createImages(9, contest1, contest2)

        val round = Round(None, 1, Some("Round 1"), contest1, Set("jury"), distribution, Round.binaryRound, active = true)
        val dbRound = roundDao.create(round)

        val dbJurors = createJurors(contest1, 3)
        val juryIds = dbJurors.map(_.id.get)

        Tools.distributeImages(dbRound, dbJurors, None)

        val selection = selectionDao.findAll()
        val byJuror = selection.groupBy(_.juryId)

        val selectedPageIds = (for ((juryId, numSelected) <- juryIds.zipWithIndex;
                                   i <- 0 until numSelected) yield {
          val pageId = byJuror(juryId)(i).pageId
          SelectionJdbc.rate(pageId, juryId, dbRound.id.get, 1)
          pageId
        }).toSet

        val round2 = Round(None, 2, Some("Round 2"), contest1, Set("jury"), 0, Round.ratesById(10), active = true, prevSelectedBy = Some(1))
        val dbRound2 = roundDao.create(round2)

        Tools.distributeImages(dbRound2, dbJurors, Some(dbRound))

        val secondRoundPageIds = selectionDao.findAll()
          .filter(_.round == dbRound2.id.get)
          .map(_.pageId).toSet

        secondRoundPageIds === selectedPageIds
      }
    }

    "create second round 2 jurors to image in the first" in {
      inMemDbApp {

        val distribution = 2
        val numImages = 2
        val numJurors = 2
        createContests(contest1, contest2)
        val images = createImages(numImages, contest1, contest2)

        val round = Round(None, 1, Some("Round 1"), contest1, Set("jury"), distribution, Round.ratesById(10), active = true)
        val dbRound = roundDao.create(round)

        val dbJurors = createJurors(contest1, numJurors)
        val juryIds = dbJurors.map(_.id.get)

        Tools.distributeImages(dbRound, dbJurors, None)

        SelectionJdbc.rate(images(0).pageId, juryIds(0), dbRound.id.get, 1)
        SelectionJdbc.rate(images(0).pageId, juryIds(1), dbRound.id.get, -1)

        SelectionJdbc.rate(images(1).pageId, juryIds(0), dbRound.id.get, 0)
        SelectionJdbc.rate(images(1).pageId, juryIds(1), dbRound.id.get, -1)

        val round2 = Round(None, 2, Some("Round 2"), contest1, Set("jury"), 0, Round.ratesById(10), active = true, prevSelectedBy = Some(1))
        val dbRound2 = roundDao.create(round2)

        Tools.distributeImages(dbRound2, dbJurors, Some(dbRound))

        val selection2 = selectionDao.findAll().filter(_.round == dbRound2.id.get)

        selection2.map(_.pageId).toSet === Set(images(0).pageId)
      }
    }
  }
}
