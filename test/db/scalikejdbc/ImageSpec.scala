package db.scalikejdbc

import org.intracer.wmua.Image
import org.specs2.mutable.Specification

class ImageSpec extends Specification with InMemDb {

  sequential

  val imageDao = ImageJdbc

  def image(id: Long) =
    Image(id, s"File:Image$id.jpg", None, None, 640, 480, Some(s"12-345-$id"))

  def addToContest(contestId: Long, images: Seq[Image]) =
    CategoryLinkJdbc.addToCategory(ContestJuryJdbc.findById(contestId).flatMap(_.categoryId).get, images)

  def createContests(contestIds: Long*) = contestIds.foreach {
    id =>
      val contest = ContestJuryJdbc.create(Some(id), "contest" + id, 2000 + id.toInt, "country" + id)
      ContestJuryJdbc.updateImages(id, Some("Images from " + contest.name))
  }


  "fresh database" should {

    "be empty" in {
      inMemDbApp {
        val images = imageDao.findAll()
        images.size === 0
      }
    }

    "insert image" in {
      inMemDbApp {
        val id = 10
        val contestId = 20
        createContests(contestId)

        val image = Image(id, "File:Image.jpg", None, None, 640, 480, Some("12-345-6789"))
        imageDao.batchInsert(Seq(image))
        addToContest(contestId, Seq(image))

        val dbi = imageDao.findById(id)
        dbi === Some(image)

        val images = imageDao.findAll()
        images === Seq(image)
      }
    }

    "find by contest " in {
      inMemDbApp {
        val (contest1, contest2) = (10, 20)
        createContests(contest1, contest2)

        val images1 = (11 to 19).map(id => image(id))
        val images2 = (21 to 29).map(id => image(id))

        imageDao.batchInsert(images1 ++ images2)
        addToContest(contest1, images1)
        addToContest(contest2, images2)

        imageDao.findByContestId(10) === images1
        imageDao.findByContestId(20) === images2
      }
    }

    "contest can share images" in {
      inMemDbApp {
        val (contest1, contest2) = (10, 20)
        createContests(contest1, contest2)

        val images1 = (11 to 19).map(id => image(id))
        val images2 = (21 to 29).map(id => image(id))

        imageDao.batchInsert(images1 ++ images2)
        addToContest(contest1, images1)
        addToContest(contest2, images2)

        imageDao.findByContestId(10) === images1
        imageDao.findByContestId(20) === images2
      }
    }

  }
}
