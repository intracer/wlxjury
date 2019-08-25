package org.intracer.wmua.cmd

import org.intracer.wmua.{ContestJury, Image, JuryTestHelpers}
import org.scalawiki.dto.{Namespace, Page}
import org.scalawiki.query.SinglePageQuery
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.concurrent.Future

class FetchImageInfoSpec extends Specification with Mockito with JuryTestHelpers {

  def contestImage(id: Long, contest: Long) =
    Image(id, s"File:Image$id.jpg", Some(s"url$id"), Some(s"pageUrl$id"), 640, 480, None, size = Some(1234))

  def imageInfo(id: Long) = new Page(Some(id), Namespace.FILE, s"File:Image$id.jpg", images = Seq(
    new org.scalawiki.dto.Image(s"File:Image$id.jpg", Some(s"url$id"), Some(s"pageUrl$id"), Some(1234), Some(640), Some(480))
  ))

  "appendImages" should {
    "get images empty" in {
      implicit ee: ExecutionEnv =>
        val category = "Category:Category Name"
        val contestId = 13
        val imageId = 11
        val images = Seq.empty[Image]
        val imageInfos = Seq.empty[Page]

        val query = mock[SinglePageQuery]

        query.withContext(Map("contestId" -> contestId.toString, "max" -> "0")) returns query
        query.imageInfoByGenerator(
          "categorymembers", "cm", namespaces = Set(Namespace.FILE), props = Set("timestamp", "user", "size", "url"), titlePrefix = None
        ) returns Future.successful(imageInfos)

        val commons = mockBot()
        commons.page(category) returns query

        val contest = ContestJury(Some(contestId), "WLE", 2015, "Ukraine", Some(category), None, None)

        FetchImageInfo(category, Seq.empty, contest, commons).apply() must be_==(images).await
    }

    "get images one image" in {
      implicit ee: ExecutionEnv =>

        val category = "Category:Category Name"
        val contestId = 13
        val imageId = 11
        val images = Seq(contestImage(imageId, contestId))
        val imageInfos = Seq(imageInfo(imageId))

        val query = mock[SinglePageQuery]
        query.withContext(Map("contestId" -> contestId.toString, "max" -> "0")) returns query
        query.imageInfoByGenerator(
          "categorymembers", "cm", namespaces = Set(Namespace.FILE), props = Set("timestamp", "user", "size", "url"), titlePrefix = None
        ) returns Future.successful(imageInfos)

        val commons = mockBot()
        commons.page(category) returns query

        val contest = ContestJury(Some(contestId), "WLE", 2015, "Ukraine", Some(category))

        FetchImageInfo(category, Seq.empty, contest, commons).apply() must be_==(images).await
    }
  }

}
