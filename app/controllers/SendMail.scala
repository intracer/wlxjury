package controllers

import play.api.libs.mailer.{Email, MailerPlugin}


class SendMail {

  /**
    * Sends an email
    *
    */
  def sendMail(fromName: String,
               fromEmail: String,
               to: Seq[String],
               cc: Seq[String] = Seq.empty,
               bcc: Seq[String] = Seq.empty,
               subject: String,
               message: String,
               richMessage: Option[String] = None,
               attachment: Option[java.io.File] = None) = {

    val email = Email(
      subject,
      to = to,
      from = s"$fromName FROM <$fromEmail>",
      bodyText = Some(message)
    )

    MailerPlugin.send(email)(play.api.Play.current)
  }

}
