package controllers

import org.specs2.mutable.Specification

class PagerSpec extends Specification {

  "pager" should {

    "list 2 page numbers" in {
      val pager = new Pager(pages = Some(2), page = 1)

      pager.pageNumbers === (1 to 2)
    }

    "list 10 page numbers" in {
      val pager = new Pager(pages = Some(10))

      pager.pageNumbers === (1 to 10)
    }

    "list 100 page numbers, page 1" in {
      val pager = new Pager(pages = Some(100), page = 1)

      pager.pageNumbers === (1 to 9) ++ (1 to 10).map(_ * 10)
    }

    "list 100 page numbers, page 11" in {
      val pager = new Pager(pages = Some(100), page = 11)

      pager.pageNumbers === Seq(1) ++ (10 to 19) ++ (2 to 10).map(_ * 10)
    }

    "list 15 page numbers, page 15" in {
      val pager = new Pager(pages = Some(15), page = 15)

      pager.pageNumbers === Seq(1) ++ (10 to 15)
    }

    "list 95 page numbers, page 90" in {
      val pager = new Pager(pages = Some(95), page = 90)

      pager.pageNumbers === Seq(1) ++ (1 to 9).map(_ * 10) ++ (91 to 95)
    }
  }
}
