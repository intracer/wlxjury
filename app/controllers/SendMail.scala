package controllers

import org.intracer.wmua.User
import org.scalawiki.MwBot
import org.scalawiki.dto.cmd.Action
import org.scalawiki.dto.cmd.email.{EmailUser, Subject, Target, Text}
import play.api.libs.mailer.{Email, MailerPlugin}

trait SendMail {
  def sendMail(from: User, to: User, subject: String, message: String)
}

object SMTPOrWikiMail extends SendMail {
  override def sendMail(from: User, to: User, subject: String, message: String): Unit = {
    val sender = Some(SendMailSMTP).filter(_ => to.email.nonEmpty).orElse(SendWikiMail.sender)
    sender.foreach(_.sendMail(from, to, subject, message))
  }
}

object SendMailSMTP extends SendMail {

  override def sendMail(fromUser: User, toUser: User, subject: String, message: String) = {
    val email = Email(
      from = s"${fromUser.fullname} <${fromUser.email}>",
      to = Seq(toUser.email),
      subject = subject,
      bodyText = Some(message)
    )
    MailerPlugin.send(email)(play.api.Play.current)
  }
}

class SendWikiMail(mwBot: MwBot) extends SendMail {

  override def sendMail(fromUser: User, toUser: User, subject: String, message: String) = {
    for (from <- fromUser.wikiAccount;
         to <- toUser.wikiAccount) {
      mwBot.run(Action(EmailUser(Target(to), Subject(subject), Text(message))))
    }
  }
}

object SendWikiMail {
  lazy val sender = init()
  def init(): Option[SendWikiMail] = {
    val configuration = play.api.Play.current.configuration
    for (login <- configuration.getString("commons.user");
         password <- configuration.getString("commons.password")) yield {
      Global.commons.login(login, password)
      new SendWikiMail(Global.commons)
    }
  }
}