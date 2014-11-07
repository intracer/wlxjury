package controllers

import play.mvc.Call

class UiSelectionDto[T](val call: T => Call, val idOpt:T, val defaultValue: SelectionItem, val values: Seq[(T, SelectionItem)], val defaultKey: T) {

  val optValues: Seq[(T, SelectionItem)] = Seq(defaultKey-> defaultValue) ++
    values.map {
      case (k, v) => (k, v)
    }

  val map: Map[T, SelectionItem] = optValues.toMap

  def current = map(idOpt)

}


object Test {

  def test(dto: UiSelectionDto[Any]) = {
    dto.call(Some(dto.map.keys.head))
  }

  private val dto: UiSelectionDto[Int] = new UiSelectionDto[Int](Int => play.api.mvc.Call("", ""), 1, new SelectionItem(""), Seq.empty, 0)
  test(dto.asInstanceOf[UiSelectionDto[Any]])

}
