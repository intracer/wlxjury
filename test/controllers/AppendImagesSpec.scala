package controllers

import db.scalikejdbc.{ContestJuryJdbc, ImageJdbc, InMemDb}
import org.intracer.wmua.{Image, JuryTestHelpers}
import org.mockito.stubbing.OngoingStubbing
import org.scalawiki.dto.{Namespace, Page, Revision}
import org.scalawiki.query.SinglePageQuery
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.concurrent.Future

class AppendImagesSpec extends Specification with Mockito with JuryTestHelpers with InMemDb {

  sequential
  stopOnFail

  val contestDao = ContestJuryJdbc
  val imageDao = ImageJdbc

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
    queryImageInfo(query, imageInfos)
    queryRevisions(query, revisions)

    val commons = mockBot()
    commons.page(category) returns query
    commons
  }

  "appendImages" should {
    val category = "Category:Category Name"
    val contestId = 10
    val idTemplate = "MonumentId"

    "get images empty" in {
      inMemDb {
        val images = Seq.empty[Image]

        val commons = mockQuery(images, category, contestId)

        val g = new GlobalRefactor(commons)

        val contest = contestDao.create(Some(contestId), "WLE", 2015, "Ukraine", Some(category))

        g.appendImages(category, "", contest)

        imageDao.findByContest(contest) === images
      }
    }

    "get one image with text" in {
      inMemDb {
        val images = Seq(image(id = 11).copy(description = Some("descr"), monumentId = Some("")))

        val commons = mockQuery(images, category, contestId)

        val contest = contestDao.create(Some(contestId), "WLE", 2015, "Ukraine", Some(category), None, None, Some("NaturalMonument"))

        val g = new GlobalRefactor(commons)
        g.appendImages(category, "", contest)

        val contestWithCategory = contestDao.findById(contest.getId).get
        eventually {
          imageDao.findByContest(contestWithCategory) === images
        }
      }
    }

    "get one image with descr and monumentId" in {
      inMemDb {
        val imageId = 11
        val descr = s"descr. {{$idTemplate|12-345-$imageId}}"
        val images = Seq(image(id = 11).copy(description = Some(descr)))

        val commons = mockQuery(images, category, contestId)

        val contest = contestDao.create(Some(contestId), "WLE", 2015, "Ukraine", Some(category), None, None, Some(idTemplate))

        val g = new GlobalRefactor(commons)
        g.appendImages(category, "", contest)

        val contestWithCategory = contestDao.findById(contest.getId).get
        eventually {
          imageDao.findByContest(contestWithCategory) === images
        }
      }
    }

    "get several images image with descr and monumentId" in {
      inMemDb {
        val images = (11 to 15).map(id => image(id).copy(description = Some(s"{{$idTemplate|12-345-$id}}")))

        val commons = mockQuery(images, category, contestId)

        val contest = contestDao.create(Some(contestId), "WLE", 2015, "Ukraine", Some(category), None, None, Some(idTemplate))

        val g = new GlobalRefactor(commons)
        g.appendImages(category, "", contest)

        val contestWithCategory = contestDao.findById(contest.getId).get
        eventually {
          imageDao.findByContest(contestWithCategory) === images
        }
      }
    }

    "update images" in {
      inMemDb {
        val images1 = (11 to 15).map(id => image(id).copy(description = Some(s"{{$idTemplate|12-345-$id}}"), monumentId = Some(s"12-345-$id")))
        val images2 = (11 to 15).map(id => image(id).copy(description = Some(s"{{$idTemplate|22-345-$id}}"), monumentId = Some(s"22-345-$id")))

        val contest = contestDao.create(Some(contestId), "WLE", 2015, "Ukraine", Some(category), None, None, Some(idTemplate))

        val g = new GlobalRefactor(mockQuery(images1, category, contestId))
        g.appendImages(category, "", contest)

        val contestWithCategory = contestDao.findById(contest.getId).get
        eventually {
          imageDao.findByContest(contestWithCategory) === images1
        }

        val g2 = new GlobalRefactor(mockQuery(images2, category, contestId))
        g2.appendImages(category, "", contestWithCategory)
        eventually {
          imageDao.findByContest(contestWithCategory) === images2
        }
      }
    }

    "shared images different categories" in {
      inMemDb {
        val images = (11 to 15).map(id => image(id).copy(description = Some(s"{{$idTemplate|12-345-$id}}")))

        val contest1 = contestDao.create(Some(contestId + 1), "WLE", 2015, "Ukraine", Some(category + 1), None, None, Some(idTemplate))
        val contest2 = contestDao.create(Some(contestId + 2), "WLE", 2015, "Europe", Some(category + 2), None, None, Some(idTemplate))

        val g = new GlobalRefactor(mockQuery(images, category + 1, contestId + 1))
        g.appendImages(category + 1, "", contest1)

        val contest1WithCategory = contestDao.findById(contest1.getId).get
        eventually {
          imageDao.findByContest(contest1WithCategory) === images
        }

        val g2 = new GlobalRefactor(mockQuery(images, category + 2, contestId + 2))
        g2.appendImages(category + 2, "", contest2)

        val contest2WithCategory = contestDao.findById(contest2.getId).get
        eventually {
          imageDao.findByContest(contest2WithCategory) === images
        }
      }
    }

    "get international images" in {
      inMemDb {
        val page = "Commons:Wiki Loves Earth 2019/Winners"
        val g = new GlobalRefactor(Global.commons)

        val contest = contestDao.create(Some(contestId + 1), "WLE", 2019, "International", Some(page), None, None, None)

        g.appendImages(page, "", contest)

        val contestWithCategory = contestDao.findById(contest.getId).get

        eventually {
          imageDao.findByContest(contestWithCategory).size === 325
        }
      }
    }
  }
}
