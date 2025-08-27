package controllers

import play.api.i18n.Messages
import play.mvc.Call
import play.twirl.api.Html

class UiSelectionDto[T](val itemUrlFunction: T => Call,
                        val selectedItemId: T,
                        val items: Seq[(T, SelectionItem)],
                        val defaultPair: Option[(T, SelectionItem)] = None) {

  val optValues: Seq[(T, SelectionItem)] = defaultPair.fold(
    Seq.empty[(T, SelectionItem)])(Seq(_)) ++
    items.map {
      case (k, v) => (k, v)
    }

  def map: Map[T, SelectionItem] = optValues.toMap

  def current: SelectionItem = map(selectedItemId)

  def toDropDown(iconOnly: Boolean = false)(implicit messages: Messages): Html =
    views.html
      .dropDownSelection(this.asInstanceOf[UiSelectionDto[Any]], iconOnly)
}

object Test {

  def test(dto: UiSelectionDto[Any]): Call = {
    dto.itemUrlFunction(Some(dto.map.keys.head))
  }

  private val dto: UiSelectionDto[Int] = new UiSelectionDto[Int](
    Int => play.api.mvc.Call("", ""),
    1,
    Seq.empty,
    Some(0 -> new SelectionItem("")))

  test(dto.asInstanceOf[UiSelectionDto[Any]])
}
