package org.intracer.wmua

import javax.mail.internet.InternetAddress

import controllers.Gallery
import db.scalikejdbc.ImageJdbc
import org.joda.time.DateTime

case class User(fullname: String,
                email: String,
                id: Option[Long],
                roles: Set[String] = Set.empty,
                password: Option[String] = None,
                contest: Option[Long],
                lang: Option[String] = None,
                createdAt: Option[DateTime] = None,
                deletedAt: Option[DateTime] = None) {

  def emailLo = email.trim.toLowerCase

  def currentContest = contest

  def hasRole(role: String) = roles.contains(role)

  def hasAnyRole(otherRoles: Set[String]) = roles.intersect(otherRoles).nonEmpty

  def sameContest(other: User): Boolean =
    (for (c <- contest; oc <- other.contest) yield c == oc)
      .getOrElse(false)

  def canEdit(otherUser: User) =
    hasRole(User.ADMIN_ROLE) && sameContest(otherUser) ||
      id == otherUser.id ||
      hasRole(User.ROOT_ROLE)

  def canViewOrgInfo(round: Round) =
    hasRole("root") ||
      (contest.contains(round.contest) &&
        hasAnyRole(Set("organizer", "admin", "root")) ||
        (roles.contains("jury") && round.juryOrgView))
}

object User {
  val JURY_ROLE = "jury"
  val JURY_ROLES = Set(JURY_ROLE)
  val ORG_COM_ROLES = Set("organizer")
  val ADMIN_ROLE = "admin"
  val ROOT_ROLE = "root"
  val ADMIN_ROLES = Set(ADMIN_ROLE, ROOT_ROLE)
  val LANGS = Map("en" -> "English", "ru" -> "Русский", "uk" -> "Українська")

  def unapplyEdit(user: User): Option[(Long, String, String, Option[String], Option[String], Option[Long], Option[String])] = {
    Some((user.id.get, user.fullname, user.email, None, Some(user.roles.toSeq.head), user.contest, user.lang))
  }

  def applyEdit(id: Long, fullname: String, email: String, password: Option[String], roles: Option[String], contest: Option[Long], lang: Option[String]): User = {
    new User(fullname, email.trim.toLowerCase, Some(id), roles.fold(Set.empty[String])(Set(_)), password, contest, lang)
  }

  def parseList(usersText: String): Seq[User] = {
    InternetAddress.parse(usersText.replaceAll("\n", ","), false).map { address =>
      User(id = None, contest = None, fullname = Option(address.getPersonal).getOrElse(""), email = address.getAddress)
    }
  }
}
