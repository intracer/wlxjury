package services

import db.scalikejdbc.{SharedTestDb, TestDb}
import org.intracer.wmua.{ContestJury, JuryTestHelpers}
import org.specs2.mutable.Specification
import org.specs2.specification.{BeforeAll, BeforeEach}
import org.specs2.mock.Mockito
import org.scalawiki.dto.{Namespace, Page}
import org.scalawiki.query.SinglePageQuery

import scala.concurrent.Future


class ContestSpec extends Specification with TestDb with Mockito with JuryTestHelpers with BeforeAll with BeforeEach {

  override def beforeAll(): Unit = SharedTestDb.init()
  override protected def before: Any = SharedTestDb.truncateAll()

  val email = "email@1.com"

  "import contests" should {
    "import Ukraine" in {

      val imagesCategory = Page("Category:Images from Wiki Loves Earth 2013 in Ukraine")
      val query = mock[SinglePageQuery]
      query.categoryMembers(Set(Namespace.CATEGORY)) returns Future.successful(Seq(imagesCategory))
      val bot = mockBot()
      bot.page("Category:Wiki Loves Earth 2013 in Ukraine") returns query

      val contestService = new ContestService(bot)

      contestService.importContests("Category:Wiki Loves Earth 2013 in Ukraine")

      val contests = contestDao.findAll()
      contests === List(
        ContestJury(
          id = Some(1),
          name = "Wiki Loves Earth",
          year = 2013,
          country = "Ukraine",
          images = Some("Category:Images from Wiki Loves Earth 2013 in Ukraine"),
          monumentIdTemplate = Some("UkrainianNaturalHeritageSite"),
          campaign = Some("wle-UA")
        )
      )
    }
  }
}
