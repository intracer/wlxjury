package api

import controllers.ContestsController
import org.intracer.wmua.ContestJury
import play.api.libs.json._
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.play._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.{endpoint, path}

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Api @Inject()(val contestsController: ContestsController) extends JsonFormat {
  val api = endpoint.in("api")

  val contests = api.in("contests")
  val createContest = contests.post.in(jsonBody[ContestJury]).out(jsonBody[ContestJury])
  val getContest = contests.in(path[Long]).get.out(jsonBody[ContestJury])
  val updateContest = contests.put.in(jsonBody[ContestJury])
  val listContests = contests.get.out(jsonBody[List[ContestJury]])

  val routes = AkkaHttpServerInterpreter().toRoute(List(
    createContest.serverLogicSuccess { contest =>
      Future(contestsController.createContest(contest))
    },
    getContest.serverLogicSuccess { id =>
      Future(contestsController.getContest(id).get)
    },
    listContests.serverLogicSuccess { _ =>
      Future(contestsController.findContests)
    }
  ))
}
