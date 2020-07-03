package controllers

import db.scalikejdbc._
import org.intracer.wmua.{Selection, _}
import org.specs2.mutable.Specification
import play.api.Application

class GallerySpec extends Specification with TestDb {

  sequential

  implicit var contest: ContestJury = _
  var round: Round = _
  var user: User = _

  def contestImage(id: Long, contestId: Long) =
    Image(id, s"File:Image$id.jpg", None, None, 640, 480, Some(s"12-345-$id"))

  def setUp(rates: Rates = Round.binaryRound) = {
    contest = contestDao.create(None, "WLE", 2015, "Ukraine")
    round = roundDao.create(
      Round(None, 1, contestId = contest.getId, rates = rates, active = true)
    )
    user = userDao.create(
      User("fullname", "email", None, Set("jury"), contestIds = contest.id)
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

  "juror" should {
    "see assigned images in binary round" in {
      withDb {
        /// prepare
        setUp(rates = Round.binaryRound)
        val images = createImages(6)
        createSelection(images.slice(0, 3), rate = 0)

        /// test
        val result = Gallery.getSortedImages(user.getId, None, round.id, "gallery")

        /// check
        result.size === 3
        result.map(_.image) === images.slice(0, 3)
        result.map(_.selection.size) === Seq(1, 1, 1)
        result.map(_.rate) === Seq(0, 0, 0)
      }
    }

    "see images filtered by rate in binary round" in {
      withDb {
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
          val result = Gallery.getSortedImages(user.getId, Some(rate), round.id, "gallery")

          /// check
          result.size === 2
          result.map(_.image) === slice(rate)
          result.map(_.selection.size) === Seq(1, 1)
          result.map(_.rate) === Seq(rate, rate)
        }
      }
    }

    "see images ordered by rate in binary round" in {
      withDb {
        /// prepare
        setUp(rates = Round.binaryRound)
        val images = createImages(6)

        def range(rate: Int) = Seq(2 + rate * 2, 4 + rate * 2)

        def slice(rate: Int) = images.slice(range(rate).head, range(rate).last)

        for (rate <- -1 to 1) {
          createSelection(slice(rate), rate = rate)
        }

        /// test
        val result = Gallery.getSortedImages(user.getId, None, round.id, "gallery")

        /// check
        result.size === 6
        result.map(_.image) === slice(1) ++ slice(0) ++ slice(-1)
        result.map(_.selection.size) === Seq.fill(6)(1)
        result.map(_.rate) === Seq(1, 1, 0, 0, -1, -1)
      }
    }

    "see images ordered by rate in rated round" in {
      withDb {
        /// prepare
        setUp(rates = Round.ratesById(10))
        val images = createImages(6)


        val selections = images.zipWithIndex.map { case (image, rate) =>
          Selection(image, user, round, rate)
        }
        selectionDao.batchInsert(selections)

        /// test
        val result = Gallery.getSortedImages(user.getId, None, round.id, "gallery")

        /// check
        result.size === 6
        result.map(_.image) === images.reverse
        result.map(_.selection.size) === Seq.fill(6)(1)
        result.map(_.rate) === (0 to 5).reverse
      }
    }
  }

  "organizer" should {
    "see rating by selection" in {
      withDb {
        /// prepare
        setUp(rates = Round.binaryRound)
        val images = createImages(10)

        val jurors = Seq(user) ++ (1 to 3).map(contestUser(_)).map(userDao.create)

        def selectedBy(n: Int, image: Image, otherRate: Int = 0): Seq[Selection] = {
          jurors.slice(0, n).map { juror =>
            Selection(image, juror, round, rate = 1)
          } ++
            jurors.slice(n, jurors.size).map { juror =>
              Selection(image, juror, round, otherRate)
            }
        }

        val selectedByX = (0 to 3).flatMap(x => selectedBy(x, images(x)))

        selectionDao.batchInsert(selectedByX)

        /// test
        val result = Gallery.getSortedImages(0, None, round.id, "gallery")

        /// check
        result.size === 4
        result.map(_.image) === Seq(
          images(3),
          images(2),
          images(1),
          images(0)
        )
        result.map(_.selection.size) === Seq(1, 1, 1, 1)
        result.map(_.rate) === Seq(3, 2, 1, 0)
      }
    }

    "see details by selection rejected not accounted" in {
      withDb {
        /// prepare
        setUp(rates = Round.binaryRound)
        val images = createImages(2)

        val jurors = (1 to 3).map(contestUser(_)).map(userDao.create)

        def rate(imageIndex: Int, jurorIndex: Int, rate: Int) =
          Selection(images(imageIndex), jurors(jurorIndex), round, rate)

        def rateN(imageIndex: Int, rates: Seq[Int]) =
          (0 to jurors.size)
            .zip(rates).map {
            case (j, r) => rate(imageIndex, j, r)
          }

        val selections =
          rateN(0, Seq(1, -1, -1)) ++
            rateN(1, Seq(0, 0, 0))

        selectionDao.batchInsert(selections)

        /// test
        val result = Gallery.getSortedImages(0, None, round.id, "filelist")

        /// check
        result.size === 2
        result.map(_.image) === Seq(images(0), images(1))

        result.map(_.selection.size) === Seq(3, 3)
        result.map(_.rate) === Seq(1, 0)
      }
    }

    "see details by selection" in {
      withDb {
        /// prepare
        setUp(rates = Round.binaryRound)
        val images = createImages(10)

        val jurors = Seq(user) ++ (1 to 3).map(contestUser(_)).map(userDao.create)

        def selectedBy(n: Int, image: Image, otherRate: Int = 0): Seq[Selection] = {
          jurors.slice(0, n).map { juror =>
            Selection(image, juror, round, rate = 1)
          } ++
            jurors.slice(n, jurors.size).map { juror =>
              Selection(image, juror, round, otherRate)
            }
        }

        val selectedByX = (0 to 3).flatMap(x => selectedBy(x, images(x)))

        selectionDao.batchInsert(selectedByX)

        /// test
        val result = Gallery.getSortedImages(0, None, round.id, "filelist")

        /// check
        result.size === 4
        result.map(_.image) === Seq(
          images(3),
          images(2),
          images(1),
          images(0)
        )
        result.map(_.rate) === Seq(1, 1, 1, 0) //  TODO why not Seq(3, 2, 1, 0)  ?
        result.map(_.selection.size) === Seq(4, 4, 4, 4)
        result.map(_.selection.map(_.rate)) === Seq(
          Seq(1, 1, 1, 0),
          Seq(1, 1, 0, 0),
          Seq(1, 0, 0, 0),
          Seq(0, 0, 0, 0)
        )
      }
    }
  }
}
