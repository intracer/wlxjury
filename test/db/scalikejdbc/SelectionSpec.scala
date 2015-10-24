package db.scalikejdbc

import db.SelectionDao
import org.intracer.wmua.Selection
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.test.Helpers._

class SelectionSpec extends Specification {

  sequential

  val selectionDao: SelectionDao = SelectionJdbc

  def inMemDbApp[T](block: => T): T = {
    running(FakeApplication(additionalConfiguration = inMemoryDatabase()))(block)
  }

  "fresh database" should {

    "be empty" in {
      inMemDbApp {
        val selection = selectionDao.findAll()
        selection.size === 0
      }
    }

    "insert selection" in {
      inMemDbApp {

        val s = Selection(-1, 20, 0, 30, 40)

        val created = selectionDao.create(s.pageId, s.rate, s.juryId, s.round, s.createdAt)

        val id = created.id

        created === s.copy(id = id)

        val dbi = selectionDao.find(id)
        dbi === Some(created)

        val selections = selectionDao.findAll()
        selections === Seq(created)
      }
    }
  }

}
