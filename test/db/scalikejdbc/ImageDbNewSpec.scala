package db.scalikejdbc

import controllers.KOATUU
import db.scalikejdbc.rewrite.ImageDbNew
import org.intracer.wmua.Region
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import play.api.i18n.{Lang, Messages}
import play.i18n

class ImageDbNewSpec extends Specification with Mockito with BeforeAll {

  override def beforeAll(): Unit = {
    KOATUU.load()
  }

  "regions" should {

    def regions(map: Map[String, Option[Int]])(messages: Messages) =
      ImageDbNew.SelectionQuery().regions(map)(messages)

    "be empty" in {
      val messages = mock[Messages]
      regions(Map.empty[String, Option[Int]])(messages) === Nil
    }

    "have level1 names" in {
      val messages = MessageStub(Map("80" -> "Kyiv"))
      val map = Map("80" -> Some(42))
      regions(map)(messages) === Seq(Region("80", "Kyiv", Some(42)))
    }

    "have level2 names" in {
      val messages = MessageStub(Map.empty)
      val map = Map("07-101" -> Some(42))
      regions(map)(messages) === Seq(Region("07-101", "Луцьк", Some(42)))
    }

    "return unknowns" in {
      val messages = MessageStub(Map.empty)
      val ids = List("", "**", "0", "00", "95", "99")
      val map = ids.zipWithIndex.toMap.view
        .mapValues(i => Option(i))
        .toMap

      regions(map)(messages) === ids.zipWithIndex
        .map { case (id, i) => Region(id, "Unknown", Some(i)) }
    }

  }
}

case class MessageStub(dict: Map[String, String]) extends Messages {

  override def apply(key: String, args: Any*): String = dict(key)

  override def apply(keys: Seq[String], args: Any*): String = ???

  override def lang: Lang = ???

  override def translate(key: String, args: Seq[Any]): Option[String] = ???

  override def isDefinedAt(key: String): Boolean = dict.contains(key)

  override def asJava: i18n.Messages = ???
}
