package controllers


case class Pager(
                  page: Int = 1,
                  offset: Option[Int] = None,
                  startPageId: Option[Long] = None,
                  var pages: Option[Int] = None) {

  private var _count: Option[Int] = None

  def display = page != 0 && pages.exists(_ > 1)

  def hasNext = pages.exists(page < _)

  def hasPrev = page > 0

  def pageSize = Pager.pageSize

  def setCount(v: Int) = {
    _count = Some(v)
    pages = Some(v / pageSize + (if (v % pageSize > 0) 1 else 0))
  }

  def pageNumbers = {
    (for (p <- pages) yield {
      val (div, mod) = (p / 10, p % 10)

      val tens = page / 10
      val digits = (1 to 9).map(_ + 10 * tens).filter(_ <= p)

      (if (tens > 0) Seq(1) else Seq.empty) ++
        (1 to div).map(_ * 10).patch(tens, digits, 0)
    }).getOrElse(Seq.empty)
  }

}

object Pager {

  def pageOffset(page: Int) = new Pager(page = page, offset = Some((page - 1) * pageSize))

  def startPageId(id: Long) = new Pager(startPageId = Some(id))

  def pageSize = 50

}
