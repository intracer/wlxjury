package db.scalikejdbc

import play.api.db.Databases

trait InMemDb {

  def inMemDbApp[T](block: => T): T = {
    Databases.withInMemory(
      name = "mydatabase",
      urlOptions = Map(
        "MODE" -> "MYSQL"
      ),
      config = Map(
        "logStatements" -> true
      )
    )(_ => block)
  }

}
