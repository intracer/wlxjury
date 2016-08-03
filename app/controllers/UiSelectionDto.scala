package controllers

import play.mvc.Call

class UiSelectionDto[T](
                         val itemUrlFunction: T => Call,
                         val idOpt: T,
                         val defaultValue: SelectionItem,
                         val values: Seq[(T, SelectionItem)],
                         val defaultKey: T,
                         val default: Boolean = true) {

  val optValues: Seq[(T, SelectionItem)] = (if (default || values.isEmpty) Seq(defaultKey -> defaultValue) else Seq.empty) ++
    values.map {
      case (k, v) => (k, v)
    }

  val map: Map[T, SelectionItem] = optValues.toMap

  def current = map(idOpt)

}


object Test {

  def test(dto: UiSelectionDto[Any]) = {
    dto.itemUrlFunction(Some(dto.map.keys.head))
  }

  private val dto: UiSelectionDto[Int] = new UiSelectionDto[Int](Int => play.api.mvc.Call("", ""), 1, new SelectionItem(""), Seq.empty, 0)
  test(dto.asInstanceOf[UiSelectionDto[Any]])

}
