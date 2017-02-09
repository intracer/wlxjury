package db.scalikejdbc

import org.intracer.wmua.Image
import org.specs2.mutable.Specification

class ImageSpec extends Specification with InMemDb {

  sequential

  val imageDao = ImageJdbc

  def contestImage(id: Long, contest: Long) =
    Image(id, contest, s"File:Image$id.jpg", None, None, 640, 480, Some(s"12-345-$id"))

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

        val image = Image(id, contestId, "File:Image.jpg", None, None, 640, 480, Some("12-345-6789"))

        imageDao.batchInsert(Seq(image))

        val dbi = imageDao.findById(id)
        dbi === Some(image)

        val images = imageDao.findAll()
        images === Seq(image)
      }
    }

    "find by contest " in {
      inMemDbApp {
        val (contest1, contest2) = (10, 20)
        val images1 = (11 to 19).map(id => contestImage(id, contest1))
        val images2 = (21 to 29).map(id => contestImage(id, contest2))

        imageDao.batchInsert(images1 ++ images2)

        imageDao.findByContest(10) === images1
        imageDao.findByContest(20) === images2
      }
    }
  }
}
