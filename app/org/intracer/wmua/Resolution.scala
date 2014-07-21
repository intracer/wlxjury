package org.intracer.wmua

import org.joda.time.DateTime
import scalikejdbc._

class Resolution(id:Int, wikiText: String, title: String, url:Option[String], date: DateTime) {

}

object Resolution extends SQLSyntaxSupport[Resolution] {
  def apply(text: String) = {
     new Resolution(0, text, text, None, DateTime.now)
  }


}