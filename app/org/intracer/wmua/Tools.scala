package org.intracer.wmua

import com.typesafe.config.ConfigFactory
import controllers.Global.commons
import controllers.GlobalRefactor
import db.scalikejdbc._
import org.intracer.wmua.cmd.{DistributeImages, ImageWithRatingSeqFilter}
import org.scalawiki.MwBot
import org.scalawiki.dto.Namespace
import org.scalawiki.wlx.dto.Contest
import play.api.Play
import scalikejdbc.{ConnectionPool, GlobalSettings, LoggingSQLAndTimeSettings}
import spray.util.pimpFuture

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source

object Tools {

  val regions = Set("44")

  def main(args: Array[String]) {
    Class.forName("com.mysql.jdbc.Driver")
    val url: String = "jdbc:mysql://localhost/wlxjury?autoReconnect=true&autoReconnectForPools=true&useUnicode=true&characterEncoding=UTF-8"
    //"jdbc:mysql://jury.wikilovesearth.org.ua/wlxjury"
    println(s"URL:" + url)

    val config = ConfigFactory.load()
    val user = config.getString("db.default.username")
    val password = config.getString("db.default.password")

    ConnectionPool.singleton(url, user, password)

    GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
      enabled = true,
      singleLineMode = false,
      printUnprocessedStackTrace = false,
      stackTraceDepth = 15,
      logLevel = 'info,
      warningEnabled = false,
      warningThresholdMillis = 3000L,
      warningLogLevel = 'warn
    )

    addMonuments()
//    addCriteria()
//    fetchMonumentDb()
//    byCity()
  }

  def fetchMonumentDb() = {
    val commons = MwBot.fromHost(controllers.Global.COMMONS_WIKIMEDIA_ORG)

    val contest = Contest.WLMUkraine(2017)
    new GlobalRefactor(commons).initLists(contest)
  }

  def distributeImages(round: Round,
                       jurors: Seq[User],
                       prevRound: Option[Round]): Unit = {
    val images = getFilteredImages(round, jurors, prevRound)

    distributeImages(round, jurors, images)
  }

  def getFilteredImages(round: Round, jurors: Seq[User], prevRound: Option[Round]): Seq[Image] = {
    getFilteredImages(round, jurors, prevRound, selectedAtLeast = round.prevSelectedBy,
      selectMinAvgRating = round.prevMinAvgRate,
      sourceCategory = round.category,
      includeCategory = round.categoryClause.map(_ > 0),
      includeRegionIds = round.regionIds.toSet,
      includeMonumentIds = round.monumentIds.toSet
    )
  }

  def distributeImages(round: Round, jurors: Seq[User], images: Seq[Image]): Unit = {
    DistributeImages(round, images, jurors).apply()
  }

  def getFilteredImages(
                        round: Round,
                        jurors: Seq[User],
                        prevRound: Option[Round],
                        includeRegionIds: Set[String] = Set.empty,
                        excludeRegionIds: Set[String] = Set.empty,
                        includeMonumentIds: Set[String] = Set.empty,
                        includePageIds: Set[Long] = Set.empty,
                        excludePageIds: Set[Long] = Set.empty,
                        includeTitles: Set[String] = Set.empty,
                        excludeTitles: Set[String] = Set.empty,
                        selectMinAvgRating: Option[Int] = None,
                        selectTopByRating: Option[Int] = None,
                        selectedAtLeast: Option[Int] = None,
                        includeJurorId: Set[Long] = Set.empty,
                        excludeJurorId: Set[Long] = Set.empty,
                        sourceCategory: Option[String] = None,
                        includeCategory: Option[Boolean] = None
                      ): Seq[Image] = {

    val catIds = sourceCategory.map { category =>
      val pages = commons.page(category).imageInfoByGenerator("categorymembers", "cm", Set(Namespace.FILE)).await
      pages.flatMap(_.id)
    }

    val (includeFromCats, excludeFromCats) = (
      for (ids <- catIds;
           include <- includeCategory)
        yield
          if (include)
            (ids, Seq.empty)
          else (Seq.empty, ids)
      ).getOrElse(Seq.empty, Seq.empty)


    val currentSelection = ImageJdbc.byRoundMerged(round.id.get).filter(iwr => iwr.selection.nonEmpty).toSet
    val existingImageIds = currentSelection.map(_.pageId)
    val existingJurorIds = currentSelection.flatMap(_.jurors)
    val mpxAtLeast = round.minMpx
    val sizeAtLeast = round.minImageSize.map(_ * 1024 * 1024)

    val contest = ContestJuryJdbc.findById(round.contest).get
    val imagesAll = prevRound.fold[Seq[ImageWithRating]](
      ImageJdbc.findByContest(contest).map(i =>
        new ImageWithRating(i, Seq.empty)
      )
    )(r =>
      ImageJdbc.byRoundMerged(r.id.get, rated = Some(true))
    )
    println("Total images: " + imagesAll.size)

    val funGens = ImageWithRatingSeqFilter.funGenerators(prevRound,
      includeRegionIds = includeRegionIds,
      excludeRegionIds = excludeRegionIds,
      includeMonumentIds = includeMonumentIds,
      includePageIds = includePageIds ++ includeFromCats.toSet,
      excludePageIds = excludePageIds ++ existingImageIds ++ excludeFromCats.toSet,
      includeTitles = includeTitles,
      excludeTitles = excludeTitles,
      includeJurorId = includeJurorId,
      excludeJurorId = excludeJurorId /*++ existingJurorIds*/,
      selectMinAvgRating = prevRound.flatMap(_ => selectMinAvgRating.filter(x => !prevRound.exists(_.isBinary))),
      selectTopByRating = prevRound.flatMap(_ => selectTopByRating),
      selectedAtLeast = prevRound.flatMap(_ => selectedAtLeast),
      mpxAtLeast = mpxAtLeast,
      sizeAtLeast = sizeAtLeast
    )

    println("Image filters:")
    funGens.foreach(println)
    val filterChain = ImageWithRatingSeqFilter.makeFunChain(funGens)

    val images = filterChain(imagesAll).map(_.image)
    println("Images after filtering: " + images.size)

    images
  }

  def updateResolution(contest: ContestJury) = {

    val commons = MwBot.fromHost(controllers.Global.COMMONS_WIKIMEDIA_ORG)

    import scala.concurrent.duration._

    val user = Play.current.configuration.getString("db.default.user").get
    val password = Play.current.configuration.getString("db.default.user").get


    Await.result(commons.login(user, password), 1.minute)

    val category = "Category:Images from Wiki Loves Earth 2014 in Ghana"
    val query = commons.page(category)

    query.imageInfoByGenerator("categorymembers", "cm", Set(Namespace.FILE)).map {
      filesInCategory =>
        val newImages = filesInCategory.flatMap(page => ImageJdbc.fromPage(page, contest)).groupBy(_.pageId)
        val existing = ImageJdbc.findAll().toSet

        for (i1 <- existing;
             i2 <- newImages.get(i1.pageId).map(seq => seq.head)
             if i1.width != i2.width || i1.height != i2.height) {
          println(s"${i2.pageId} ${i1.title}  ${i1.width}x${i1.height} -> ${i2.width}x${i2.height}")
          ImageJdbc.updateResolution(i1.pageId, i2.width, i2.height)
        }
    }
  }

  def globalRefactor = {
    val commons = MwBot.fromHost(MwBot.commons)
    new GlobalRefactor(commons)
  }

  def initImages(): Unit = {
    val contest = ContestJuryJdbc.findById(77L).get

    globalRefactor.appendImages(contest.images.get, "", contest)

    val round = RoundJdbc.findById(133L).get

    distributeImages(round, round.jurors, None)
  }

  def addUsers(contest: ContestJury, number: Int) = {
    val country = contest.country.replaceAll("[ \\-\\&]", "")
    val jurors = (1 to number).map(i => country + "ESPCJuror" + i)

    val orgCom = (1 to 1).map(i => country + "ESPCOrgCom")
    //
    val logins = //Seq.empty
      jurors ++ orgCom

    //  (1 to number).map(i => country + "Jury" + i) ++ Seq(country + "OrgCom")
    val passwords = logins.map(s => UserJdbc.randomString(8)) // !! i =>

    logins.zip(passwords).foreach {
      case (login, password) =>
        UserJdbc.create(
          login,
          login, UserJdbc.sha1(contest.country + "/" + password),
          if //(login.contains("Jury"))
          (jurors.contains(login))
            Set("jury")
          else Set("organizer"),
          contest.id,
          Some("en"))
    }

    logins.zip(passwords).foreach {
      case (login, password) =>
        println(s"$login / $password")
    }
  }

  def verifySpain(): Unit = {
    val newImages = ImageJdbc.byRatingMerged(0, 112).map(_.title).toSet
    val selected = Source.fromFile("spain.txt")(scala.io.Codec.UTF8).getLines().map(_.replace(160.asInstanceOf[Char], ' ').trim).toSet

    val absent = selected -- newImages

    absent.foreach(println)
  }

  def removeIneligible() = {
    val category = "Category:Obviously ineligible submissions for ESPC 2015 in Ukraine"
    val query = commons.page(category)

    query.imageInfoByGenerator("categorymembers", "cm", Set(Namespace.FILE)).map {
      filesInCategory =>
        val ids = filesInCategory.flatMap(_.id)

        ids.foreach {
          id => SelectionJdbc.destroyAll(id)
        }
    }
  }

  def fixRound() = {
    val category = "Category:Non-photographic media from European Science Photo Competition 2015"
    val query = commons.page(category)

    query.imageInfoByGenerator("categorymembers", "cm", Set(Namespace.FILE)).map {
      filesInCategory =>
        val ids = filesInCategory.flatMap(_.id).toSet

        val thisCountry = ImageJdbc.findByContestId(77L).map(_.pageId).toSet

        val intersection = thisCountry intersect ids

        intersection.foreach {
          id => SelectionJdbc.setRound(id, 133L, 138L)
        }
    }
  }

  def addMonuments() = {
      val contest = ContestJuryJdbc.findById(67).get
      val source = contest.images.get

      new GlobalRefactor(commons).updateMonuments(source, contest)
  }

  def addCriteria() = {
    val roundId = 315
    val round = RoundJdbc.findById(roundId).get
    val images = Seq.empty
    val jurors = UserJdbc.findByRoundSelection(roundId)
    val selection = SelectionJdbc.byRound(roundId)

    DistributeImages(round, images, jurors).addCriteriaRates(selection)
  }

  def byCity() = {

    val contest = ContestJuryJdbc.findById(67).get

    val ukWiki = MwBot.fromHost(MwBot.ukWiki)
    //    val cities = Seq("Бар (місто)", "Бершадь", "Гайсин", "Гнівань", "Жмеринка", "Іллінці",
    //      "Калинівка", "Козятин", "Ладижин", "Липовець", "Могилів-Подільський", "Немирів",
    //      "Погребище", "Тульчин", "Хмільник", "Шаргород", "Ямпіль")

    val cities = Seq("Великі Мости", "Мости Великі",
      "Дубляни",
      "Жидачів",
      "Новий Розділ",
      "Пустомити",
      "Стебник"   )


    val allImages = ImageJdbc.findByContest(contest)

    val imageDb = allImages.groupBy(_.monumentId.getOrElse(""))

    val allMonuments = MonumentJdbc.findAll()

    val all = allMonuments.filter { m =>
      val city = m.city.getOrElse("").replaceAll("\\[", " ").replaceAll("\\]", " ")
      m.photo.isDefined && cities.map(_ + " ").exists(city.contains) && !city.contains("район") && Set("46").contains(m.regionId)
    }

    def cityShort(city: String) = cities.find(city.contains).getOrElse("").split(" ")(0)

    def page(city: String) = "User:Ilya/Львівська область/" + city

    all.groupBy(m => cityShort(m.city.getOrElse(""))).foreach {
      case (city, monuments) =>

        val galleries = monuments.map {
          m =>
            val images = imageDb.getOrElse(m.id, Nil)
            val gallery = org.scalawiki.dto.Image.gallery(images.map(_.title))

            s"""== ${m.name.replaceAll("\\[\\[", "[[:uk:")} ==
               |'''Рік:''' ${m.year.getOrElse("")}, '''Адреса:''' ${m.place.getOrElse("")}, '''Тип:''' ${m.typ.getOrElse("")},
               |'''Охоронний номер:''' ${m.stateId.getOrElse("")}\n""".stripMargin +
              gallery
        }
        val text = galleries.mkString("\n")
        val title = page(city)
        ukWiki.page(title).edit(text)
    }

    val list = cities.map(city => s"#[[${page(city)}|$city]]").mkString("\n")
    ukWiki.page("User:Ilya/Львівська область").edit(list)
  }

}
