package controllers

import org.intracer.wmua.User
import play.api.libs.mailer.{Email, MailerPlugin}

trait SendMail {
  def sendMail(from: User, to: User, subject: String, message: String)
}

class SendMailSMTP extends SendMail {

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
