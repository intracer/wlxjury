package org.intracer.wmua.cmd

import org.intracer.wmua.{ContestJury, Image, JuryTestHelpers}
import org.scalawiki.dto.{Namespace, Page, Revision}
import org.scalawiki.query.SinglePageQuery
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.concurrent.Future

class FetchImageTextSpec extends Specification with Mockito with JuryTestHelpers {

  def contestImage(id: Long, contest: Long) =
    Image(id, s"File:Image$id.jpg", None, None, 0, 0, Some(s"12-345-$id"))

  def revision(id: Long, text: String) = new Page(Some(id), Namespace.FILE, s"File:Image$id.jpg", revisions = Seq(
    new Revision(Some(id + 100), Some(id), content = Some(text))
  ))

  "appendImages" should {
    "get images empty" in {
      implicit ee: ExecutionEnv =>
        val category = "Category:Category Name"
        val contestId = 13
        val images = Seq.empty[Image]
        val revisions = Seq.empty[Page]

        val query = mock[SinglePageQuery]
        query.withContext(Map("contestId" -> contestId.toString, "max" -> "0")) returns query
        query.revisionsByGenerator("categorymembers", "cm",
          Set.empty, Set("content", "timestamp", "user", "comment"), limit = "50", titlePrefix = None
        ) returns Future.successful(revisions)

        val commons = mockBot()
        commons.page(category) returns query

        val contest = ContestJury(Some(contestId), "WLE", 2015, "Ukraine", Some(category))

        FetchImageText(category, contest, None, commons).apply() must be_==(images).await
    }

    "get images one image with text" in {
      implicit ee: ExecutionEnv =>
        val category = "Category:Category Name"
        val contestId = 13
        val imageId = 11
        val images = Seq(contestImage(imageId, contestId).copy(description = Some("descr"), monumentId = Some(""), author = Some("")))
        val revisions = Seq(revision(imageId, "{{Information|description=descr}}"))

        val query = mock[SinglePageQuery]
        query.withContext(Map("contestId" -> contestId.toString, "max" -> "0")) returns query
        query.revisionsByGenerator("categorymembers", "cm",
          Set.empty, Set("content", "timestamp", "user", "comment"), limit = "50", titlePrefix = None
        ) returns Future.successful(revisions)

        val commons = mockBot()
        commons.page(category) returns query

        val contest = ContestJury(Some(contestId), "WLE", 2015, "Ukraine", Some(category), Some(0), None)

        FetchImageText(category, contest, None, commons).apply() must be_==(images).await
      }

    "get images one image with descr and monumentId" in {
      implicit ee: ExecutionEnv =>

        val category = "Category:Category Name"
        val idTemplate = "monumentId"
        val contestId = 13
        val imageId = 11
        val descr = s"descr. {{$idTemplate|12-345-$imageId}}"
        val images = Seq(contestImage(imageId, contestId).copy(description = Some(descr), author = Some("")))
        val revisions = Seq(revision(imageId, s"{{Information|description=$descr}}"))

        val query = mock[SinglePageQuery]
        query.withContext(Map("contestId" -> contestId.toString, "max" -> "0")) returns query
        query.revisionsByGenerator("categorymembers", "cm",
          Set.empty, Set("content", "timestamp", "user", "comment"), limit = "50", titlePrefix = None
        ) returns Future.successful(revisions)

        val commons = mockBot()
        commons.page(category) returns query

        val contest = ContestJury(Some(contestId), "WLE", 2015, "Ukraine", Some(category), Some(0), None)

        FetchImageText(category, contest, Some(idTemplate), commons).apply() must be_==(images).await
    }

    "one image from page" in {
      implicit ee: ExecutionEnv =>

        val page = "Commons:Wiki Loves Earth 2019/Winners"
        val idTemplate = "monumentId"
        val contestId = 13
        val imageId = 11
        val descr = s"descr. {{$idTemplate|12-345-$imageId}}"
        val images = Seq(contestImage(imageId, contestId).copy(description = Some(descr), author = Some("")))
        val revisions = Seq(revision(imageId, s"{{Information|description=$descr}}"))

        val query = mock[SinglePageQuery]
        query.withContext(Map("contestId" -> contestId.toString, "max" -> "0")) returns query
        query.revisionsByGenerator("images", "im",
          Set.empty, Set("content", "timestamp", "user", "comment"), limit = "50", titlePrefix = None
        ) returns Future.successful(revisions)

        val commons = mockBot()
        commons.page(page) returns query

        val contest = ContestJury(Some(contestId), "WLE", 2019, "International", Some(page), Some(0), None)

        FetchImageText(page, contest, Some(idTemplate), commons).apply() must be_==(images).await
    }
  }
}
