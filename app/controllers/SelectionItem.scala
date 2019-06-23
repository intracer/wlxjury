package controllers

import play.api.i18n.Messages
import play.api.i18n.MessagesProvider

case class SelectionItem(text: String,
                         icon: Option[String] = None,
                         localized: Boolean = true) {

  def localizedText(implicit messagesProvider: MessagesProvider) = {
    if (localized) {
      Messages(text)
    } else {
      text
    }
  }
}
