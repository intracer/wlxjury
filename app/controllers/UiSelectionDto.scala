package controllers

import play.api.i18n.{Lang, Messages}
import play.mvc.Call

class UiSelectionDto[T](
                         val itemUrlFunction: T => Call,
                         val selectedItemId: T,
                         val items: Seq[(T, SelectionItem)],
                         val defaultPair: Option[(T, SelectionItem)] = None) {

  val optValues: Seq[(T, SelectionItem)] = defaultPair.fold(Seq.empty[(T, SelectionItem)])(Seq(_)) ++
    items.map {
      case (k, v) => (k, v)
    }

  def map = optValues.toMap

  def current = map(selectedItemId)

  def toDropDown(iconOnly: Boolean = false)(implicit lang: Lang, messages: Messages) =
    views.html.dropDownSelection(this.asInstanceOf[UiSelectionDto[Any]], iconOnly)
}

object Test {

  def test(dto: UiSelectionDto[Any]) = {
    dto.itemUrlFunction(Some(dto.map.keys.head))
  }

  private val dto: UiSelectionDto[Int] = new UiSelectionDto[Int](Int => play.api.mvc.Call("", ""), 1, Seq.empty, Some(0 -> new SelectionItem("")))

  test(dto.asInstanceOf[UiSelectionDto[Any]])
}
