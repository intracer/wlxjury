package db.scalikejdbc

import controllers.KOATUU
import db.scalikejdbc.rewrite.ImageDbNew
import org.intracer.wmua.Region
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import play.api.i18n.{Lang, Messages}

class ImageDbNewSpec extends Specification with Mockito with BeforeAll {

  override def beforeAll() = {
    KOATUU.load()
  }

  "regions" should {

    def regions(map: Map[String, Int])(messages: Messages) = ImageDbNew.SelectionQuery().regions(map)(messages)

    "be empty" in {
      val messages = mock[Messages]
      regions(Map.empty[String, Int])(messages) === Nil
    }

    "have level1 names" in {
      val messages = MessageStub(Map("80" -> "Kyiv"))
      val map = Map("80" -> 42)
      regions(map)(messages) === Seq(Region("80", "Kyiv", 42))
    }

    "have level2 names" in {
      val messages = MessageStub(Map.empty)
      val map = Map("07-101" -> 42)
      regions(map)(messages) === Seq(Region("07-101", "Луцьк", 42))
    }

  }
}

case class MessageStub(dict: Map[String, String]) extends Messages {

  override def apply(key: String, args: Any*): String = dict(key)

  override def apply(keys: Seq[String], args: Any*): String = ???

  override def lang: Lang = ???

  override def translate(key: String, args: Seq[Any]): Option[String] = ???

  override def isDefinedAt(key: String): Boolean = dict.contains(key)
}
