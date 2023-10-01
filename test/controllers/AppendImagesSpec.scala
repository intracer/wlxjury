package controllers

import db.scalikejdbc.TestDb
import org.intracer.wmua.{Image, JuryTestHelpers}
import org.mockito.stubbing.OngoingStubbing
import org.scalawiki.MwBot
import org.scalawiki.dto.{Namespace, Page, Revision}
import org.scalawiki.query.SinglePageQuery
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import services.{ImageService, MonumentService}

import scala.concurrent.{ExecutionContext, Future}

class AppendImagesSpec extends Specification with Mockito with JuryTestHelpers with TestDb {

  sequential
  stopOnFail

  implicit val ec: ExecutionContext = ExecutionContext.global

  "appendImages" should {
    val category = "Category:Category Name"
    val contestId = 10
    val idTemplate = "MonumentId"

    "get images empty" in {
      withDb {
        val images = Nil
        val ic = mockService(images, category, contestId)

        val contest = contestDao.create(Some(contestId), "WLE", 2015, "Ukraine", Some(category))
        ic.appendImages(category, "", contest)
        imageDao.findByContest(contest) === images
      }
    }

    "get one image with text" in {
      withDb {
        val images = Seq(image(id = 11).copy(description = Some("descr"), monumentId = None))
        val contest = contestDao.create(Some(contestId), "WLE", 2015, "Ukraine", Some(category), None, None, Some("NaturalMonument"))

        val ic = mockService(images, category, contestId)
        ic.appendImages(category, "", contest)

        val contestWithCategory = contestDao.findById(contest.getId).get
        eventually {
          imageDao.findByContest(contestWithCategory) === images
        }
      }
    }

    "get one image with descr and monumentId" in {
      withDb {
        val imageId = 11
        val descr = s"descr. {{$idTemplate|12-345-$imageId}}"
        val images = Seq(image(id = 11).copy(description = Some(descr)))

        val contest = contestDao.create(Some(contestId), "WLE", 2015, "Ukraine", Some(category), None, None, Some(idTemplate))

        val ic = mockService(images, category, contestId)
        ic.appendImages(category, "", contest)

        val contestWithCategory = contestDao.findById(contest.getId).get
        eventually {
          imageDao.findByContest(contestWithCategory) === images
        }
      }
    }

    "get several images image with descr and monumentId" in {
      withDb {
        val images = (11 to 15).map(id => image(id).copy(description = Some(s"{{$idTemplate|12-345-$id}}")))
        val contest = contestDao.create(Some(contestId), "WLE", 2015, "Ukraine", Some(category), None, None, Some(idTemplate))

        val ic = mockService(images, category, contestId)
        ic.appendImages(category, "", contest)

        val contestWithCategory = contestDao.findById(contest.getId).get
        eventually {
          imageDao.findByContest(contestWithCategory) === images
        }
      }
    }

    "get images from list" in {
      val images = (11 to 15).map(id => image(id).copy(description = Some(s"{{$idTemplate|12-345-$id}}")))
      withDb {
        val contest = contestDao.create(Some(contestId + 1), "WLE", 2019, "International", Some(category), None, None, None)
        val ic = mockService(images, category, contestId)
        val imageList = images.map(_.title).mkString(System.lineSeparator)
        ic.appendImages("", imageList, contest)

        val contestWithCategory = contestDao.findById(contest.getId).get
        eventually {
          imageDao.findByContest(contestWithCategory) === images
        }
      }
    }

    "update images" in {
      withDb {
        val images1 = (11 to 15).map(id => image(id).copy(description = Some(s"{{$idTemplate|12-345-$id}}"), monumentId = Some(s"12-345-$id")))
        val images2 = (11 to 15).map(id => image(id).copy(description = Some(s"{{$idTemplate|22-345-$id}}"), monumentId = Some(s"22-345-$id")))

        val contest = contestDao.create(Some(contestId), "WLE", 2015, "Ukraine", Some(category), None, None, Some(idTemplate))

        val ic = mockService(images1, category, contestId)
        ic.appendImages(category, "", contest)

        val contestWithCategory = contestDao.findById(contest.getId).get
        eventually {
          imageDao.findByContest(contestWithCategory) === images1
        }

        val ic2 =  mockService(images2, category, contestId)
        ic2.appendImages(category, "", contestWithCategory)
        eventually {
          imageDao.findByContest(contestWithCategory) === images2
        }
      }
    }

    "shared images different categories" in {
      withDb {
        val images = (11 to 15).map(id => image(id).copy(description = Some(s"{{$idTemplate|12-345-$id}}")))

        val contest1 = contestDao.create(Some(contestId + 1), "WLE", 2015, "Ukraine", Some(category + 1), None, None, Some(idTemplate))
        val contest2 = contestDao.create(Some(contestId + 2), "WLE", 2015, "Europe", Some(category + 2), None, None, Some(idTemplate))

        val ic = mockService(images, category + 1, contestId + 1)
        ic.appendImages(category + 1, "", contest1)

        val contest1WithCategory = contestDao.findById(contest1.getId).get
        eventually {
          imageDao.findByContest(contest1WithCategory) === images
        }

        val ic2 = mockService(images, category + 2, contestId + 2)
        ic2.appendImages(category + 2, "", contest2)

        val contest2WithCategory = contestDao.findById(contest2.getId).get
        eventually {
          imageDao.findByContest(contest2WithCategory) === images
        }
      }
    }

    "get international images" in {
      withDb {
        val page = "Commons:Wiki Loves Earth 2019/Winners"
        val is = new ImageService(Global.commons, mock[MonumentService])
        val contest = contestDao.create(Some(contestId + 1), "WLE", 2019, "International", Some(page), None, None, None)
        is.appendImages(page, "", contest)
        val contestWithCategory = contestDao.findById(contest.getId).get

        eventually {
          imageDao.findByContest(contestWithCategory).size must be_>=(350)
        }
      }
    }
  }

  private def image(id: Long) =
    Image(id, s"File:Image$id.jpg", Some(s"url$id"), None, 640, 480, Some(s"12-345-$id"), size = Some(1234))

  private def imageInfo(id: Long) = new Page(Some(id), Some(Namespace.FILE), s"File:Image$id.jpg", images = Seq(
    new org.scalawiki.dto.Image(s"File:Image$id.jpg", Some(s"url$id"), Some(s"pageUrl$id"), Some(1234), Some(640), Some(480))
  ))

  private def revision(id: Long, text: String) = new Page(Some(id), Some(Namespace.FILE), s"File:Image$id.jpg", revisions = Seq(
    new Revision(Some(id + 100), Some(id), content = Some(text))
  ))

  private def mockService(images: Seq[Image], category: String, contestId: Long): ImageService = {
    val commons = mockQuery(images, category, contestId)
    new ImageService(commons, mock[MonumentService])
  }

  private def mockQuery(images: Seq[Image], category: String, contestId: Long): MwBot = {
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

  private def queryImageInfo(query: SinglePageQuery, imageInfos: Seq[Page]): OngoingStubbing[Future[Seq[Page]]] = {
    query.imageInfoByGenerator("categorymembers", "cm",
      namespaces = Set(Namespace.FILE), props = Set("timestamp", "user", "size", "url"), titlePrefix = None
    ) returns Future.successful(imageInfos)
  }

  private def queryRevisions(query: SinglePageQuery, revisions: Seq[Page]): OngoingStubbing[Future[Seq[Page]]] = {
    query.revisionsByGenerator("categorymembers", "cm",
      Set(Namespace.FILE), Set("content", "timestamp", "user", "comment"), limit = "50", titlePrefix = None
    ) returns Future.successful(revisions)
  }

}
