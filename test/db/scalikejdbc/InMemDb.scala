package db.scalikejdbc

import play.api.test.FakeApplication
import play.api.test.Helpers._

trait InMemDb {

  def inMemDbApp[T](block: => T): T = {
    running(FakeApplication(additionalConfiguration = inMemoryDatabase()))(block)
  }

}
