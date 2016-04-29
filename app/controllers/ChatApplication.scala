package controllers

import db.scalikejdbc.RoundJdbc
import org.intracer.wmua.{Comment, CommentJdbc, User}
import org.joda.time.DateTime
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
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
        val round = RoundJdbc.current(user)

        //        cache(round.id) {
        //          Concurrent.broadcast[JsValue]
        //        }

        implicit val commentFormat = Json.format[Comment]

        val messages = round.flatMap(_.id).fold(Seq.empty[JsValue]) { roundId =>
          CommentJdbc.findByRound(roundId).map(m => Json.toJson(m))
        }
        Ok(views.html.chat("Chat", user, user.id.get, user, messages, round.toSeq, gallery = true))
  }

  /** Controller action for POSTing chat messages */
  def postMessage =
    withAuthBP(parse.json)({
      user =>
        req =>
          val body: JsValue = req.body.asInstanceOf[JsObject]

          val round = RoundJdbc.current(user)

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

          CommentJdbc.create(userId, username, round.flatMap(_.id).get, room, text, at)

          Ok
    }, roles = Set(User.ADMIN_ROLE, "jury"))


  /** Enumeratee for filtering messages based on room */
  def filter(room: String) = Enumeratee.filter[JsValue] { json: JsValue => (json \ "room").as[String] == room }

  /** Enumeratee for detecting disconnect of SSE stream */
  def connDeathWatch(addr: String): Enumeratee[JsValue, JsValue] =
    Enumeratee.onIterateeDone { () => println(addr + " - SSE disconnected") }

  /** Controller action serving activity based on room */
  def chatFeed(round: String) = withAuth {
    user =>
      implicit req =>

        val round = RoundJdbc.current(user)

        //        val future: Future[Result] = cache(round) {
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
