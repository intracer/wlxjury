package org.intracer.wmua

trait HasId {
  def id: Option[Long]

  def getId: Long = id.getOrElse {
    throw new NoSuchElementException(s"no id in $toString")
  }

}
