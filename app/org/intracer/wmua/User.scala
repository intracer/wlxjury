package org.intracer.wmua

import java.time.ZonedDateTime
import javax.mail.internet.InternetAddress

import play.api.data.validation.{Constraints, Invalid, Valid}

import scala.util.Try

case class User(fullname: String,
                email: String,
                id: Option[Long] = None,
                roles: Set[String] = Set.empty,
                password: Option[String] = None,
                contestId: Option[Long] = None,
                lang: Option[String] = None,
                createdAt: Option[ZonedDateTime] = None,
                deletedAt: Option[ZonedDateTime] = None,
                wikiAccount: Option[String] = None,
                hasWikiEmail: Boolean = false,
                accountValid: Boolean = true
               ) extends HasId {

  def emailLo = email.trim.toLowerCase

  def currentContest = contestId

  def hasRole(role: String) = roles.contains(role)

  def hasAnyRole(otherRoles: Set[String]) = roles.intersect(otherRoles).nonEmpty

  def sameContest(other: User): Boolean = isInContest(other.contestId)

  def isInContest(refContestId: Option[Long]): Boolean =
    (for (c <- contestId; oc <- refContestId) yield c == oc)
      .getOrElse(false)

  def isAdmin(refContestId: Option[Long]) =
    hasRole(User.ADMIN_ROLE) && isInContest(refContestId) ||
      hasRole(User.ROOT_ROLE)

  def canEdit(otherUser: User) =
    isAdmin(otherUser.contestId) ||
      id == otherUser.id

  def canViewOrgInfo(round: Round) =
    hasRole("root") ||
      (contestId.contains(round.contestId) &&
        hasAnyRole(Set("organizer", "admin", "root")) ||
        (roles.contains("jury") && round.juryOrgView))

  def description: String = Seq(fullname, wikiAccount.fold("")(u => "User:" + u), email).mkString(" / ")
}

object User {
  val JURY_ROLE = "jury"
  val JURY_ROLES = Set(JURY_ROLE)
  val ORG_COM_ROLES = Set("organizer")
  val ADMIN_ROLE = "admin"
  val ROOT_ROLE = "root"
  val ADMIN_ROLES = Set(ADMIN_ROLE, ROOT_ROLE)
  val LANGS = Map("en" -> "English", "fr" -> "Français",  "ru" -> "Русский", "uk" -> "Українська")

  def unapplyEdit(user: User): Option[(Long, String, Option[String], String, Option[String], Option[String], Option[Long], Option[String])] = {
    Some((user.getId, user.fullname, user.wikiAccount, user.email, None, Some(user.roles.toSeq.head), user.contestId, user.lang))
  }

  def applyEdit(id: Long, fullname: String, wikiAccount: Option[String], email: String, password: Option[String],
                roles: Option[String], contest: Option[Long], lang: Option[String]): User = {
    new User(fullname, email.trim.toLowerCase, Some(id), roles.fold(Set.empty[String])(Set(_)), password, contest, lang,
      wikiAccount = wikiAccount)
  }

  val emailConstraint = Constraints.emailAddress

  def parseList(usersText: String): Seq[User] = {

    def fromUserName(str: String): Option[User] = {
      val withoutPrefix = str.replaceFirst("User:", "")
      Some(withoutPrefix).filter(_.trim.nonEmpty).map { _ =>
        User(id = None, contestId = None, fullname = "", email = "", wikiAccount = Some(withoutPrefix))
      }
    }

    def fromInternetAddress(internetAddress: InternetAddress): Option[User] = {
      Constraints.emailAddress()(internetAddress.getAddress) match {
        case Valid =>
          Some(User(id = None, contestId = None,
            fullname = Option(internetAddress.getPersonal).getOrElse(""),
            email = internetAddress.getAddress))
        case Invalid(_) => None
      }
    }

    usersText.split("[,|\n|]|,[ ]*\n").flatMap { str =>
      Try {
        InternetAddress.parse(str, false)
      }.toOption.flatMap(_.headOption.flatMap(fromInternetAddress)).orElse(fromUserName(str))
    }
  }
}
