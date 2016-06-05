package controllers

import org.intracer.wmua.{ContestJury, User}
import org.specs2.mutable.Specification

class AdminSpec extends Specification {
  val sender = User("Admin User", "email@server.com", None, contest = None)

  "fill temlpate" should {

    "fill contest info" in {
      val contest = ContestJury(name = "Wiki Loves Earth", year = 2016, country = "Ukraine", images = None, id = None)
      val template = "Organizing committee of {{ContestType}} {{ContestYear}} {{ContestCountry}} is glad to welcome you as a jury member"
      val filled = Admin.fillGreeting(template, contest, sender)
      filled === "Organizing committee of Wiki Loves Earth 2016 Ukraine is glad to welcome you as a jury member"
    }
  }
}
