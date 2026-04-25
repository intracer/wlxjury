package graphql

import munit.FunSuite
import sangria.parser.QueryParser

class SchemaValidationSpec extends FunSuite {

  test("schema.graphql parses without errors") {
    val sdl = scala.io.Source.fromResource("graphql/schema.graphql").mkString
    val result = QueryParser.parse(sdl)
    assert(result.isSuccess, result.failed.map(_.getMessage).getOrElse("parse failed"))
  }
}
