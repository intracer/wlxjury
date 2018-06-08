package db.scalikejdbc

import org.intracer.wmua.Selection
import org.specs2.mutable.Specification

class SelectionSpec extends Specification with InMemDb {

  sequential

  val selectionDao = SelectionJdbc
  val time = now

  def sameTime(s: Seq[Selection]) = s.map(_.copy(createdAt = Some(time)))

  def findAll() = sameTime(selectionDao.findAll())

  "fresh database" should {
    "be empty" in {
      inMemDbApp {
        val selection = selectionDao.findAll()
        selection.size === 0
      }
    }

    "insert selection" in {
      inMemDbApp {

        val s = Selection(-1, 20, 0, 30, Some(40), Some(now))

        val created = selectionDao.create(s.pageId, s.rate, s.juryId, s.roundId, s.createdAt)

        val id = created.getId

        created === s.copy(id = Some(id))

        val dbi = selectionDao.findById(id)
        dbi === Some(created)

        val selections = selectionDao.findAll()
        selections === Seq(created)
      }
    }

    "batch insert selections" in {
      inMemDbApp {
        val s = sameTime(Seq(
          Selection(-1, 1, 0, 10, Some(20)),
          Selection(-1, 2, 1, 11, Some(21)),
          Selection(-1, 3, -1, 12, Some(22))
        ))

        selectionDao.batchInsert(s)

        val selections = sameTime(selectionDao.findAll())
        selections.map(_.id) === Seq(1,2,3)
        selections.map(_.copy()) === s
      }
    }

    "rate selections" in {

      inMemDbApp {
        val s = sameTime(Seq(
          Selection(1, 1, 0, 10, Some(20)),
          Selection(2, 1, 0, 11, Some(20)),
          Selection(3, 2, 0, 10, Some(20)),
          Selection(4, 2, 0, 11, Some(20)),
          Selection(5, 1, 0, 10, Some(21))
        ))

        selectionDao.batchInsert(s)

        selectionDao.rate(1, 10, 20, 1)

        findAll() === s.head.copy(rate = 1) +: s.tail

        selectionDao.rate(1, 10, 20, -1)
        findAll() === s.head.copy(rate = -1) +: s.tail

        selectionDao.rate(1, 10, 20, 0)
        findAll() === s
      }
    }

  }
}
