package db.scalikejdbc

import org.intracer.wmua.Selection
import org.specs2.mutable.Specification

class SelectionSpec extends Specification with TestDb {

  sequential

  val time = now

  private def sameTime(s: Seq[Selection]): Seq[Selection] = s.map(_.copy(createdAt = Some(time)))
  private def noIds(s: Seq[Selection]): Seq[Selection] = s.map(_.copy(id = None))

  private def findAll(): Seq[Selection] = sameTime(selectionDao.findAll())

  "fresh database" should {
    "be empty" in {
      withDb {
        selectionDao.findAll() === Nil
      }
    }

    "insert selection" in {
      withDb {

        val s = Selection(-1, 20, 0, 30, Some(40), Some(now))

        val created = selectionDao.create(s.pageId, s.rate, s.juryId, s.roundId, s.createdAt)
        val id = created.getId
        created === s.copy(id = Some(id))
        selectionDao.findById(id) === Some(created)
        selectionDao.findAll() === Seq(created)
      }
    }

    "batch insert selections" in {
      withDb {
        val s = sameTime(Seq(
          Selection(20, 1, 0, 10),
          Selection(21, 2, 1, 11),
          Selection(22, 3, -1, 12)
        ))

        selectionDao.batchInsert(s)

        val selections = sameTime(selectionDao.findAll())
        selections.flatMap(_.id) === Seq(1,2,3)
        noIds(selections) === s
      }
    }

    "rate selections" in {

      withDb {
        val s = sameTime(Seq(
          Selection(pageId = 1, roundId = 20, juryId = 10),
          Selection(pageId = 1, roundId = 20, juryId = 11),
          Selection(pageId = 2, roundId = 20, juryId = 10),
          Selection(pageId = 2, roundId = 20, juryId = 11),
          Selection(pageId = 1, roundId = 21, juryId = 10)
        ))

        selectionDao.batchInsert(s)

        selectionDao.rate(1, 10, 20, 1)

        noIds(findAll()) === s.head.copy(rate = 1) +: s.tail

        selectionDao.rate(1, 10, 20, -1)
        noIds(findAll()) === s.head.copy(rate = -1) +: s.tail

        selectionDao.rate(1, 10, 20, 0)
        noIds(findAll()) === s
      }
    }

  }
}
