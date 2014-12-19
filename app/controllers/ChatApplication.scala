package controllers

import org.intracer.wmua.{Comment, CommentJdbc, Round, User}
import org.joda.time.DateTime
import play.api.libs.EventSource
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.{Concurrent, Enumeratee, Enumerator}
import play.api.libs.json._
import play.api.mvc._


object ChatApplication extends Controller with Secured {

  /** Central hub for distributing chat messages */

  import spray.caching.{Cache, LruCache}

  val (chatOut, chatChannel) = Concurrent.broadcast[JsValue]

  val cache: Cache[(Enumerator[JsValue], Concurrent.Channel[JsValue])] = LruCache()

  /** Controller action serving AngularJS chat page */
  def index = withAuth {
    user =>
      implicit request =>
        val round = Round.current(user)

//        cache(round.id) {
//          Concurrent.broadcast[JsValue]
//        }

        implicit val commentFormat = Json.format[Comment]

        val messages = CommentJdbc.findByRound(round.id.toInt).map(m => Json.toJson(m))
        Ok(views.html.chat("Chat", user, user.id.toInt, user, messages, Seq(round), gallery = true))
  }

  /** Controller action for POSTing chat messages */
  def postMessage =
    withAuth(parse.json) ({
      user =>
        req =>
          val body: JsValue = req.body.asInstanceOf[JsObject]

          val round = Round.current(user)

//          cache(round.id) {
//            Concurrent.broadcast[JsValue]
//          }.map {
//            case (_, chatChannel) =>
              chatChannel.push(body)
//          }

          val userId = (body \ "userId").asInstanceOf[JsNumber].value.toInt
          val text = (body \ "body").asInstanceOf[JsString].value
          val username = (body \ "username").asInstanceOf[JsString].value
          val at = DateTime.now.toString
          val room = (body \ "room").asInstanceOf[JsString].value.toInt

          CommentJdbc.create(userId, username, round.id.toInt, room, text, at)

          Ok
    }, roles = Set(User.ADMIN_ROLE, "jury"))


  /** Enumeratee for filtering messages based on room */
  def filter(room: String) = Enumeratee.filter[JsValue] { json: JsValue => (json \ "room").as[String] == room}

  /** Enumeratee for detecting disconnect of SSE stream */
  def connDeathWatch(addr: String): Enumeratee[JsValue, JsValue] =
    Enumeratee.onIterateeDone { () => println(addr + " - SSE disconnected")}

  /** Controller action serving activity based on room */
  def chatFeed(round: String) = withAuth {
    user =>
      implicit req =>

        val round = Round.current(user)

//        val future: Future[SimpleResult] = cache(round) {
//          Concurrent.broadcast[JsValue]
//        }.map {
//          case (chatOut, _) =>

            println(req.remoteAddress + " - SSE connected")
            Ok.feed(chatOut
              //              &> filter(room)
              &> Concurrent.buffer(50)
              &> connDeathWatch(req.remoteAddress)
              &> EventSource()
            ).as("text/event-stream")
//        }
//
//        AsyncResult(future)
  }

}
