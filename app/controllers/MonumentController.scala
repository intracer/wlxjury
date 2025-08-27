package controllers

import db.scalikejdbc.MonumentJdbc
import org.scalawiki.wlx.MonumentDB
import org.scalawiki.wlx.dto.{Contest, ContestType, Monument, SpecialNomination}
import org.scalawiki.wlx.query.MonumentQuery
import org.scalawiki.wlx.stat.ContestStat
import play.api.i18n.I18nSupport
import play.api.mvc.{AbstractController, ControllerComponents}

import javax.inject.Inject

class MonumentController @Inject()(cc: ControllerComponents)
    extends AbstractController(cc)
    with I18nSupport {

  def list = Action { implicit request =>
    val monuments = MonumentJdbc.findAll(Some(20))
    Ok(views.html.monuments(monuments))
  }

  def byId(id: String) = Action { implicit request =>
    val monuments = MonumentJdbc.find(id).toSeq
    Ok(views.html.monuments(monuments))
  }


}
