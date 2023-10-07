package services

import db.scalikejdbc.User
import org.scalawiki.MwBot
import org.scalawiki.dto.cmd.Action
import org.scalawiki.dto.cmd.email.{EmailUser, Subject, Target, Text}
import play.api.libs.mailer.{Email, MailerClient}

import javax.inject.Inject

trait SendMail {
  def sendMail(from: User, to: User, subject: String, message: String): Unit
}

class SMTPOrWikiMail @Inject()(sendMailSMTP: SendMailSMTP) extends SendMail {
  override def sendMail(from: User,
                        to: User,
                        subject: String,
                        message: String): Unit = {
    val sender = Some(sendMailSMTP).filter(_ => to.email.nonEmpty) //.orElse(SendWikiMail.sender)
    sender.foreach(_.sendMail(from, to, subject, message))
  }
}

class SendMailSMTP @Inject()(mailerClient: MailerClient) extends SendMail {

  override def sendMail(fromUser: User,
                        toUser: User,
                        subject: String,
                        message: String): Unit = {
    val email = Email(
      from = s"${fromUser.fullname} <${fromUser.email}>",
      to = Seq(toUser.email),
      subject = subject,
      bodyText = Some(message)
    )
    mailerClient.send(email)
  }
}

class SendWikiMail(mwBot: MwBot) extends SendMail {

  override def sendMail(fromUser: User,
                        toUser: User,
                        subject: String,
                        message: String): Unit = {
    for (from <- fromUser.wikiAccount;
         to <- toUser.wikiAccount) {
      mwBot.run(Action(EmailUser(Target(to), Subject(subject), Text(message))))
    }
  }
}

object SendWikiMail {
  lazy val sender: Option[SendWikiMail] = init()

  def init(): Option[SendWikiMail] = {
//    val configuration = play.api.Play.current.configuration
//    for (login <- configuration.getString("commons.user");
//         password <- configuration.getString("commons.password")) yield {
//      Global.commons.login(login, password)
//      new SendWikiMail(Global.commons)
//    }
    None
  }
}
