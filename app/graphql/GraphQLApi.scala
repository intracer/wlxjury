package graphql

import org.intracer.wmua.ContestJury
import caliban._
import caliban.schema.Schema.auto._
import caliban.schema.ArgBuilder.auto._

object GraphQLApi {
  case class ContestId(id: String)
  case class Queries(contests: List[ContestJury], contest: ContestId => Option[ContestJury])

  def getContests: List[ContestJury] = Nil
  def getContest(id: String): Option[ContestJury] = None

  val queries = Queries(getContests, args => getContest(args.id))

  val api = graphQL(RootResolver(queries))

  def main(args: Array[String]): Unit = {
    println(api.render)
  }

}
