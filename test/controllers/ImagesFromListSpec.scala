package controllers

import db.scalikejdbc.{CategoryJdbc, ContestJuryJdbc, ImageJdbc, InMemDb}
import org.intracer.wmua.JuryTestHelpers
import org.specs2.mutable.Specification

class ImagesFromListSpec extends Specification with JuryTestHelpers with InMemDb {

  val contestDao = ContestJuryJdbc
  val imageDao = ImageJdbc
  val categoryDao = CategoryJdbc

  val categoryName = "Category:Category Name"
  val contestId = 10

  "contest" should {

    "import from list" in {
      inMemDb {
        val list = resourceAsString("ArmeniaImageList.txt")
        val names = list.split("\n")
        val uniqueNormalizedNames = names.map(_.replace("_", " ")).distinct

        val contest = contestDao.create(Some(contestId), "WLE", 2020, "Armenia", Some(categoryName))

        val g = new GlobalRefactor(Global.commons)
        g.appendImages(categoryName, list, contest)

        val dbCategories = categoryDao.findAll()

        val images = imageDao.findByCategory(dbCategories.last.id)

        val missingImages = uniqueNormalizedNames.toSet -- images.map(_.title).toSet
        missingImages === Set.empty

        images.size === uniqueNormalizedNames.length
      }
    }
  }
}
