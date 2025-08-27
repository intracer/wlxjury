package controllers

import db.scalikejdbc.{CategoryJdbc, ContestJuryJdbc, ImageJdbc, TestDb}
import org.intracer.wmua.JuryTestHelpers
import org.specs2.mutable.Specification
import services.{ImageService, MonumentService}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class ImagesFromListSpec extends Specification with JuryTestHelpers with TestDb {

  val categoryDao = CategoryJdbc

  val categoryName = "Category:Category Name"
  val contestId = 10

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  "contest" should {

    "import from list" in {
      withDb {
        val list = resourceAsString("ArmeniaImageList.txt")
        val names = list.split(System.lineSeparator)
        val uniqueNormalizedNames = names.map(_.replace("_", " ")).distinct

        val contest = contestDao.create(Some(contestId), "WLE", 2020, "Armenia", Some(categoryName))

        val ic = new ImageService(Global.commons, mock[MonumentService])
        ic.appendImages(categoryName, list, contest)

        val dbCategories = categoryDao.findAll()

        val images = imageDao.findByCategory(dbCategories.last.id)

        val missingImages = uniqueNormalizedNames.toSet -- images.map(_.title).toSet
        missingImages === Set.empty

        images.size === uniqueNormalizedNames.length
      }
    }
  }
}
