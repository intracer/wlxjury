package controllers

import db.scalikejdbc.{ContestJuryJdbc, ImageJdbc}
import org.intracer.wmua.{Image, JuryTestHelpers}
import org.mockito.stubbing.OngoingStubbing
import org.scalawiki.dto.{Namespace, Page, Revision}
import org.scalawiki.query.SinglePageQuery
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.test.Helpers._

import scala.concurrent.Future

class GlobalRefactorSpec extends Specification with Mockito with JuryTestHelpers {

  sequential

  val contestDao = ContestJuryJdbc
  val imageDao = ImageJdbc

  def inMemDbApp[T](block: => T): T = {
    running(FakeApplication(additionalConfiguration = inMemoryDatabase()))(block)
  }

  def image(id: Long) =
    Image(id, s"File:Image$id.jpg", Some(s"url$id"), None, 640, 480, Some(s"12-345-$id"), size = Some(1234))

  def imageInfo(id: Long) = new Page(Some(id), Namespace.FILE, s"File:Image$id.jpg", images = Seq(
    new org.scalawiki.dto.Image(s"File:Image$id.jpg", Some(s"url$id"), Some(s"pageUrl$id"), Some(1234), Some(640), Some(480))
  ))

  def revision(id: Long, text: String) = new Page(Some(id), Namespace.FILE, s"File:Image$id.jpg", revisions = Seq(
    new Revision(Some(id + 100), Some(id), content = Some(text))
  ))

  def queryImageInfo(query: SinglePageQuery, imageInfos: Seq[Page]): OngoingStubbing[Future[Seq[Page]]] = {
    query.imageInfoByGenerator(
      "categorymembers", "cm", namespaces = Set(Namespace.FILE), props = Set("timestamp", "user", "size", "url"), titlePrefix = None
    ) returns Future.successful(imageInfos)
  }

  def queryRevisions(query: SinglePageQuery, revisions: Seq[Page]): OngoingStubbing[Future[Seq[Page]]] = {
    query.revisionsByGenerator("categorymembers", "cm",
      Set.empty, Set("content", "timestamp", "user", "comment"), limit = "50", titlePrefix = None
    ) returns Future.successful(revisions)
  }

  def mockQuery(images: Seq[Image], category: String, contestId: Long) = {
    val imageInfos = images.map(i => imageInfo(i.pageId))
    val revisions = images.map(i => revision(i.pageId, s"{{Information|description=${i.description.getOrElse("")}}}"))

    val query = mock[SinglePageQuery]
    query.withContext(Map("contestId" -> contestId.toString, "max" -> "0")) returns query
    query.withContext(Map("contestId" -> contestId.toString, "max" -> "0")) returns query
    queryImageInfo(query, imageInfos)
    queryRevisions(query, revisions)

    val commons = mockBot()
    commons.page(category) returns query
    commons
  }

  "appendImages" should {
    "get images empty" in {
      inMemDbApp {
        val category = "Category:Category Name"
        val contestId = 13
        val images = Seq.empty[Image]

        val commons = mockQuery(images, category, contestId)

        val g = new GlobalRefactor(commons)

        val contest = contestDao.create(Some(contestId), "WLE", 2015, "Ukraine", Some(category))

        g.appendImages(category, "", contest)

        imageDao.findByContest(contest) === images
      }
    }

    "get images one image with text" in {
      inMemDbApp {
        val category = "Category:Category Name"
        val contestId = 13
        val imageId = 11
        val images = Seq(image(imageId).copy(description = Some("descr"), monumentId = Some("")))

        val commons = mockQuery(images, category, contestId)

        val contest = contestDao.create(Some(contestId), "WLE", 2015, "Ukraine", Some(category), None, None, Some("NaturalMonument"))

        val g = new GlobalRefactor(commons)
        g.appendImages(category, "", contest)

        val contestWithCategory = contestDao.findById(contest.id.get).get
        eventually {
          imageDao.findByContest(contestWithCategory) === images
        }
      }
    }

    "get images one image with descr and monumentId" in {
      inMemDbApp {
        val category = "Category:Category Name"
        val idTemplate = "monumentId"
        val contestId = 13
        val imageId = 11
        val descr = s"descr. {{$idTemplate|12-345-$imageId}}"
        val images = Seq(image(imageId).copy(description = Some(descr)))

        val commons = mockQuery(images, category, contestId)

        val contest = contestDao.create(Some(contestId), "WLE", 2015, "Ukraine", Some(category), None, None, Some(idTemplate))

        val g = new GlobalRefactor(commons)
        g.appendImages(category, "", contest)

        val contestWithCategory = contestDao.findById(contest.id.get).get
        eventually {
          imageDao.findByContest(contestWithCategory) === images
        }
      }
    }

  }
}
