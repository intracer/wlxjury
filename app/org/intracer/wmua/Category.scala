package org.intracer.wmua

case class Category(id: Long, title: String)

case class CategoryLink(categoryId: Long, pageId: Long)