package controllers


class SendMail {

  import org.apache.commons.mail._

import scala.util.Try

  //private val emailHost = Play.configuration.getString("email.host").get

  /**
   *  Sends an email
   *  @return Whether sending the email was a success
   */
  def sendMail(from: (String, String), // (email -> name)
               to: Seq[String],
               cc: Seq[String] = Seq.empty,
               bcc: Seq[String] = Seq.empty,
               subject: String,
               message: String,
               richMessage: Option[String] = None,
               attachment: Option[java.io.File] = None) = {

    val mail: Email =
      new SimpleEmail().setMsg(message)

    mail.setHostName("smtp.googlemail.com")
    mail.setSmtpPort(465)
    mail.setAuthenticator(new DefaultAuthenticator("intracer", ""))
    mail.setSSLOnConnect(true)

  //mail.setHostName(emailHost)

  to.foreach(mail.addTo)
  cc.foreach(mail.addCc)
  bcc.foreach(mail.addBcc)

  val preparedMail = mail.
    setFrom(from._2, from._1).
    setSubject(subject)

  // Send the email and check for exceptions
  Try(preparedMail.send).isSuccess
}

//def sendMailAsync(...) = Future(sendMail(...))
}
