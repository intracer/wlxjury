package db.scalikejdbc

import db.ImageDao
import org.intracer.wmua.Image
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.test.Helpers._

class ImageSpec extends Specification {

  sequential

  val imageDao: ImageDao = ImageJdbc

  def inMemDbApp[T](block: => T): T = {
    running(FakeApplication(additionalConfiguration = inMemoryDatabase()))(block)
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

        val image = Image(id, contestId, "File:Image.jpg", "url", "pageUrl", 640, 480, Some("12-345-6789"))

        imageDao.batchInsert(Seq(image))

        val dbi = imageDao.find(id)
        dbi === Some(image)

        val images = imageDao.findAll()
        images === Seq(image)
      }
    }

  }

}
