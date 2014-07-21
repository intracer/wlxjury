package controllers

import org.intracer.wmua.MonumentJdbc
import play.api.mvc.{Action, Controller}

object Monuments extends Controller {

  def list = Action {
    implicit request =>

      val monuments = MonumentJdbc.findAll()
      Ok(views.html.monuments(monuments))
  }

}
