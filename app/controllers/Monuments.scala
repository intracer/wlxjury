package controllers

import org.intracer.wmua.MonumentJdbc
import play.api.mvc.{Action, Controller}

object Monuments extends Controller {

  def list = Action {
    implicit request =>

      val monuments = MonumentJdbc.findAll().take(20)
      Ok(views.html.monuments(monuments))
  }

  def byId(id: String) = Action {
    implicit request =>

      val monuments = MonumentJdbc.find(id).toSeq
      Ok(views.html.monuments(monuments))
  }


}
