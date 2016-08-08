package controllers

import org.intracer.wmua.ImageWithRating

class Pager(files: Seq[ImageWithRating]) {

  def at(pageId: Long) = files.indexWhere(_.pageId == pageId) / (filesPerPage + 1) + 1

  def filesPerPage = files.size / pages

  def pageFiles(page: Int) = files.slice((page - 1) * (filesPerPage + 1), Math.min(page * (filesPerPage + 1), files.size))

  def pages = Pager.pages(files)

}

object Pager {

  def pages[T](files: Seq[T]) = Math.max(Math.min(10, files.size / 20), 1)

  def filesPerPage[T](files: Seq[T]): Int = files.size / pages(files)

}
