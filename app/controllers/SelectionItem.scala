package controllers

import play.api.i18n.Messages
import play.api.i18n.Lang

case class SelectionItem(text: String,
                         icon: Option[String] = None,
                         localized: Boolean = true) {

  def localizedText(implicit lang: Lang, messages: Messages) = {
    if (localized) {
      Messages(text)
    } else {
      text
    }
  }
}
