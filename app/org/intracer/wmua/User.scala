package org.intracer.wmua

import controllers.Gallery
import org.joda.time.DateTime

import scala.collection.mutable

case class User(fullname: String,
                email: String,
                id: Option[Long],
                roles: Set[String] = Set.empty,
                password: Option[String] = None,
                contest: Long,
                lang: Option[String] = None,
                files: mutable.Buffer[ImageWithRating] = mutable.Buffer.empty,
                createdAt: DateTime = DateTime.now,
                deletedAt: Option[DateTime] = None) {

  def emailLo = email.trim.toLowerCase

  def roundFiles(roundId: Long) = Gallery.userFiles(this, roundId)

  //    def withFiles(files: Seq[ImageWithRating]) = this.copy(files = files)

  //def roles = Seq("jury")

  def canViewOrgInfo(round: Round) =
    roles.intersect(Set("organizer", "admin")).nonEmpty || (roles.contains("jury") && round.juryOrgView)

}

object User {
  val JURY_ROLES = Set("jury")
  val ORG_COM_ROLES = Set("organizer")
  val ADMIN_ROLE = "admin"
  val ADMIN_ROLES = Set(ADMIN_ROLE)
  val LANGS = Map("en" -> "English", "ru" -> "Русский", "uk"-> "Українська")

  def unapplyEdit(user: User): Option[(Long, String, String, Option[String], Option[String], Long, Option[String])] = {
    Some((user.id.get, user.fullname, user.email, None, Some(user.roles.toSeq.head), user.contest, user.lang))
  }

  def applyEdit(id: Long, fullname: String, email: String, password: Option[String], roles: Option[String], contest: Long, lang: Option[String]): User = {
    new User(fullname, email.trim.toLowerCase, Some(id), roles.fold(Set.empty[String])(Set(_)), password, contest, lang)
  }
}
