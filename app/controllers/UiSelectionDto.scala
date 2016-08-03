package controllers

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

  val map: Map[T, SelectionItem] = optValues.toMap

  def current = map(selectedItemId)

}


object Test {

  def test(dto: UiSelectionDto[Any]) = {
    dto.itemUrlFunction(Some(dto.map.keys.head))
  }

  private val dto: UiSelectionDto[Int] = new UiSelectionDto[Int](Int => play.api.mvc.Call("", ""), 1, Seq.empty, Some(0 -> new SelectionItem("")))

  test(dto.asInstanceOf[UiSelectionDto[Any]])
}
