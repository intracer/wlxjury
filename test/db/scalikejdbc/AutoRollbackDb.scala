package db.scalikejdbc

import scalikejdbc.DBSession
import scalikejdbc.specs2.mutable.AutoRollback

/** Use as a per-test anonymous instance inside specs2 test blocks:
 *
 *  {{{
 *    class MySpec extends Specification with BeforeAll {
 *      override def beforeAll(): Unit = SharedTestDb.init()
 *
 *      "insert something" in new AutoRollbackDb {
 *        // implicit val session: DBSession is provided by AutoRollback
 *        myDao.create(...)
 *        myDao.findAll() === Seq(...)
 *      }
 *    }
 *  }}}
 *
 *  Each `new AutoRollbackDb` opens a transaction and rolls it back after the test.
 *  The spec class must call SharedTestDb.init() in beforeAll to ensure the
 *  ScalikeJDBC ConnectionPool is ready.
 */
trait AutoRollbackDb extends AutoRollback with TestDb {
  // AutoRollback provides `implicit val session` (rollback session).
  // Override TestDb.testSession to point to the same rollback session,
  // avoiding two ambiguous implicits of type DBSession in scope.
  override implicit val testSession: DBSession = session
}
