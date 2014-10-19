package models

import org.apache.commons.io.FileUtils
import org.specs2.execute.AsResult
import org.specs2.specification.AroundExample
import play.api.db.DB
import play.api.test.FakeApplication
import play.api.test.Helpers._

import scala.collection.immutable.SortedSet

trait WithTestDatabase extends AroundExample {

  val testDb = Map("db.default.url" -> "jdbc:mysql://localhost/wlxjury_test", "evolutionplugin" -> "disabled")

  def around[T: AsResult](t: => T) = {
    implicit val app = FakeApplication(additionalConfiguration = testDb)
    running(app) {
      new SchemaManager(app).dropCreateSchema()
      AsResult(t)
    }
  }

  private class SchemaManager(val app: FakeApplication) {

    private val evolutions = parseEvolutions

    private def parseEvolutions:Iterable[Evolution] = {
      import scala.collection.JavaConversions._
      val evolutions = FileUtils.listFiles(app.getFile("conf/evolutions/default/"), Array("sql"), false)
      evolutions.flatMap { evolution =>
        val evolutionContent = FileUtils.readFileToString(evolution)
        val splittedEvolutionContent = evolutionContent.split("# --- !Ups")
        if (splittedEvolutionContent.size < 2) {
          None
        } else {
          val upsDowns = splittedEvolutionContent(1).split("# --- !Downs")
          val index = evolution.getName.replace(".sql", "").toInt
          Some(new Evolution(index, upsDowns(0), if (upsDowns.size >=2) upsDowns(1) else ""))
        }
      }
    }

    def dropCreateSchema()(implicit app: FakeApplication) {
      dropSchema()
      createSchema()
    }

    private def dropSchema()(implicit app: FakeApplication) = {

      DB.withTransaction { conn =>
        conn.createStatement().executeUpdate("DROP DATABASE wlxjury_test;")
        conn.createStatement().executeUpdate("CREATE DATABASE wlxjury_test;")
      }
    }

    private def createSchema() = {
      orderEvolutions(new EvolutionsOrderingAsc).foreach { _.runUp(app) }
    }

    private def orderEvolutions(ordering: Ordering[Evolution]) = {
      evolutions.foldLeft(SortedSet[Evolution]()(ordering)) { (treeSet, evolution) =>
        treeSet + evolution
      }
    }
  }

  private class EvolutionsOrderingDesc extends Ordering[Evolution] {
    override def compare(a: Evolution, b: Evolution): Int = b.index compare a.index
  }

  private class EvolutionsOrderingAsc extends Ordering[Evolution] {
    def compare(a: Evolution, b: Evolution) = a.index compare b.index
  }

  private case class Evolution(index: Int, up: String, down: String) {

    private val upQueries = up.trim.split(";")
    private val downQueries = down.trim.split(";")

    def runUp(implicit app: FakeApplication) = {
      runQueries(upQueries)
    }

    def runDown(implicit app: FakeApplication) = {
      runQueries(downQueries)
    }

    private def runQueries(queries: Array[String])(implicit app: FakeApplication) {
      DB.withTransaction { conn =>
        queries.filterNot(_.isEmpty).foreach { q =>
          println("Running query: " + q)
          conn.createStatement.execute(q) }
      }
    }

  }}