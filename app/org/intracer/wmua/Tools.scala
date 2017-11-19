package org.intracer.wmua

import com.typesafe.config.ConfigFactory
import controllers.Global.commons
import controllers.GlobalRefactor
import db.scalikejdbc._
import org.intracer.wmua.cmd.{DistributeImages, ImageWithRatingSeqFilter}
import org.scalawiki.MwBot
import org.scalawiki.dto.{Image, Namespace}
import org.scalawiki.wlx.{ImageDB, ListFiller, MonumentDB}
import org.scalawiki.wlx.dto.{Contest, Monument}
import org.scalawiki.wlx.query.MonumentQuery
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

//    addMonuments()
//    newlyPictured()
    //    addCriteria()
    //    fetchMonumentDb()
    byCity()
    //fillLists()
  }

  def fetchMonumentDb() = {
    val commons = MwBot.fromHost(controllers.Global.COMMONS_WIKIMEDIA_ORG)

    val contest = Contest.WLMUkraine(2017)
    new GlobalRefactor(commons).updateLists(contest)
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
      excludeJurorId = excludeJurorId /*++ existingJurorIds*/ ,
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
    val contest = ContestJuryJdbc.findById(64).get
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

  def convertImages(images: Seq[Image]) = {
    images.map { i =>
      new org.scalawiki.dto.Image(
        title = i.title,
        url = i.url,
        pageUrl = i.pageUrl,
        size = i.size.map(_.toLong),
        width = Some(i.width),
        height = Some(i.height),
        author = i.author,
        uploader = None,
        year = None,
        date = None,
        monumentId = i.monumentId,
        pageId = Some(i.pageId)
      )
    }
  }

  def newlyPictured() = {
    val contestJury2017 = ContestJuryJdbc.findById(64).get
    val contestJuryAll = ContestJuryJdbc.findById(66).get

    val allImages = ImageJdbc.findByContest(contestJuryAll)
    val images2017 = ImageJdbc.findByContest(contestJury2017)

    val allMonuments = MonumentJdbc.findAll()

    val contest1 = Contest.WLEUkraine(2017)
    //    val mdb = new MonumentDB(contest1, allMonuments)
    //    val idb = new ImageDB(contest1, convertImages(allImages), Some(mdb))

    val allById = allImages.groupBy(_.monumentId.getOrElse(""))
    val byId2017 = images2017.groupBy(_.monumentId.getOrElse(""))

    val ids = allById.keySet
    val newImages = ids.flatMap { id =>
      val im2017 = byId2017.getOrElse(id, Nil).toSet
      val imAll = allById.getOrElse(id, Nil).toSet

      if (im2017.size == imAll.size) im2017 else Nil
    }

    val newImagesById = newImages.groupBy(_.monumentId.getOrElse(""))

    val monumentQuery = MonumentQuery.create(contest1)
    val monuments = monumentQuery.byMonumentTemplate().filter(m => newImagesById.keySet.contains(m.id))


    val galleries = monuments.map {
      m =>
        val images = newImagesById.getOrElse(m.id, Set.empty[Image])
        val gallery = org.scalawiki.dto.Image.gallery(images.map(_.title).toSeq)

        s"""\n== ${m.name.replaceAll("\\[\\[", "[[:uk:")} ==\n""" + gallery
    }

    val contestTitle =contest1.contestType.name
    commons.page(s"Commons:$contestTitle in Ukraine 2017 newly pictured").edit(galleries.mkString("\n"))
  }


  def fillLists() = {

    val contestJury = ContestJuryJdbc.findById(67).get

    val allImages = ImageJdbc.findByContest(contestJury)

    val allMonuments = MonumentJdbc.findAll()

    val contest1 = Contest.WLMUkraine(2016)
    val mdb = new MonumentDB(contest1, allMonuments)
    val idb = new ImageDB(contest1, convertImages(allImages), Some(mdb))

    ListFiller.fillLists(mdb, idb)
  }

  def byCity() = {

    val contest = ContestJuryJdbc.findById(67).get

    val codes = Map("80" -> "Київ",
      "07" -> "Волинська",
      "68" -> "Хмельницька",
      "05" -> "Вінницька",
      "35" -> "Кіровоградська",
      "65" -> "Херсонська",
      "63" -> "Харківська",
      "01" -> "АР Крим",
      "32" -> "Київська",
      "61" -> "Тернопільська",
      "18" -> "Житомирська",
      "48" -> "Миколаївська",
      "46" -> "Львівська",
      "14" -> "Донецька",
      "44" -> "Луганська",
      "74" -> "Чернігівська",
      "12" -> "Дніпропетровська",
      "73" -> "Чернівецька",
      "71" -> "Черкаська",
      "59" -> "Сумська",
      "26" -> "Івано-Франківська",
      "56" -> "Рівненська",
      "85" -> "Севастополь",
      "23" -> "Запорізька",
      "53" -> "Полтавська",
      "21" -> "Закарпатська",
      "51" -> "Одеська")

    val cityMap = Map("АР Крим" -> Seq(
      "Алупка",
      "Алушта",
      "Армянськ",
      "Бахчисарай",
      "Білогірськ",
      "Джанкой",
      "Красноперекопськ", "Яни Капу",
      "Саки",
      "Старий Крим",
      "Судак",
      "Щолкіне"),

      "Донецька область" -> Seq(
        "Авдіївка",
        "Амвросіївка",
        "Білицьке",
        "Білозерське",
        "Бунге", "Юнокомунарівськ",
        "Волноваха",
        "Вуглегірськ",
        "Вугледар",
        "Гірник",
        "Дебальцеве",
        "Добропілля",
        "Докучаєвськ",
        "Жданівка",
        "Залізне", "Артемове",
        "Зугрес",
        "Іловайськ",
        "Кальміуське", "Комсомольське",
        "Красногорівка",
        "Курахове",
        "Лиман", "Красний Лиман",
        "Мар'їнка",
        "Миколаївка",
        "Моспине",
        "Новоазовськ",
        "Новогродівка",
        "Родинське",
        "Світлодарськ",
        "Святогірськ",
        "Селидове",
        "Сіверськ",
        "Соледар",
        "Торецьк", "Дзержинськ",
        "Українськ",
        "Хрестівка", "Кіровське",
        "Часів Яр",
        "Ясинувата"),

      "Луганська область" -> Seq(
        "Алмазна",
        "Боково-Хрустальне", "Вахрушеве",
        "Вознесенівка", "Червонопартизанськ",
        "Гірське",
        "Голубівка", "Кіровськ",
        "Довжанськ", "Свердловськ",
        "Зимогір'я",
        "Золоте",
        "Зоринськ",
        "Ірміно", "Теплогірськ",
        "Кипуче", "Артемівськ",
        "Кремінна",
        "Лутугине",
        "Міусинськ",
        "Молодогвардійськ",
        "Новодружеськ",
        "Олександрівськ",
        "Первомайськ",
        "Перевальськ",
        "Петрово-Краснопілля", "Петровське",
        "Попасна",
        "Привілля",
        "Сватове",
        "Сорокине", "Краснодон",
        "Старобільськ",
        "Суходільськ",
        "Щастя"),

      "Львівська область" -> Seq(
        "Белз",
        "Бібрка",
        "Борислав",
        "Броди",
        "Буськ",
        "Великі Мости",
        "Винники",
        "Глиняни",
        "Городок",
        "Добромиль",
        "Дубляни",
        "Жидачів",
        "Жовква",
        "Золочів",
        "Кам’янка-Бузька",
        "Комарно",
        "Миколаїв",
        "Моршин",
        "Мостиська",
        "Новий Калинів",
        "Новий Розділ",
        "Новояворівськ",
        "Перемишляни",
        "Пустомити",
        "Рава-Руська",
        "Радехів",
        "Рудки",
        "Самбір",
        "Сколе",
        "Сокаль",
        "Соснівка",
        "Старий Самбір",
        "Стебник",
        "Судова Вишня",
        "Трускавець",
        "Турка",
        "Угнів",
        "Хирів",
        "Ходорів",
        "Яворів"),

      "Миколаївська область" -> Seq(
        "Баштанка",
        "Вознесенськ",
        "Нова Одеса",
        "Новий Буг",
        "Очаків",
        "Снігурівка",
        "Южноукраїнськ"),

      "Тернопільська область" -> Seq(
        "Бережани",
        "Борщів",
        "Бучач",
        "Заліщики",
        "Збараж",
        "Зборів",
        "Копичинці",
        "Кременець",
        "Ланівці",
        "Монастирська",
        "Підгайці",
        "Почаїв",
        "Скалат",
        "Теребовля",
        "Хоростків",
        "Чортків",
        "Шумськ"),

      "Харківська область" -> Seq(
        "Балаклія",
        "Барвінкове",
        "Богодухів",
        "Валки",
        "Вовчанськ",
        "Дергачі",
        "Зміїв",
        "Красноград",
        "Куп’янськ",
        "Люботин",
        "Мерефа",
        "Первомайський",
        "Південне",
        "Чугуїв"),

      "Хмельницька область" -> Seq(
        "Волочиськ",
        "Городок",
        "Деражня",
        "Дунаївці",
        "Ізяслав",
        "Красилів",
        "Нетішин",
        "Полонне",
        "Славута",
        "Старокостянтинів",
        "Шепетівка"),

      "Чернівецька область" -> Seq(
        "Вашківці",
        "Вижниця",
        "Герца",
        "Заставна",
        "Кіцмань",
        "Новодністровськ",
        "Новоселиця",
        "Сокиряни",
        "Сторожинець",
        "Хотин"),

      "Чернігівська область" -> Seq(
        "Батурин",
        "Бахмач",
        "Борзна",
        "Городня",
        "Ічня",
        "Корюківка",
        "Мена",
        "Новгород-Сіверський",
        "Носівка",
        "Остер",
        "Семенівна",
        "Сновськ"))

    val ukWiki = MwBot.fromHost(MwBot.ukWiki)

    val allImages = ImageJdbc.findByContest(contest)

    val imageDb = allImages.groupBy(_.monumentId.getOrElse(""))

    val allMonuments = MonumentJdbc.findAll()

    val links = for (longName <- cityMap.keys.toSeq.filter(m => m.contains("Чернівецька") || m.contains("Чернігівська")).sorted;
                     cities <- cityMap.get(longName);
                     shortName = longName.replace(" область", "");
                     code <- codes.collectFirst { case (code, name) if name == shortName => code })
      yield
        regionGallery(allMonuments, imageDb, code, longName, cities)


    val text = links.mkString("\n")
    val title = s"User:Ilya/ДНАББ"
//    ukWiki.page(title).edit(text)
  }


  def regionGallery(allMonuments: Seq[Monument], imageDb: Map[String, Seq[Image]], regionCode: String, regionName: String, cities: Seq[String]) = {
    val ukWiki = MwBot.fromHost(MwBot.ukWiki)

    def cityShort(city: String): String = cities.find(city.contains).getOrElse("").split(" ")(0)

    def page(city: String) = s"User:Ilya/$regionName/$city"

    println(s"Processing: $regionName ($regionCode)")
    println(s"Cities: $cities")

    val regionMonuments = allMonuments.filter { m =>
      val city = m.city.getOrElse("").replaceAll("\\[", " ").replaceAll("\\]", " ")
      (m.photo.isDefined || imageDb.contains(m.id)) && cities.map(_ + " ").exists(city.contains) && !city.contains("район") && Set(regionCode).contains(m.regionId)
    }

    println(s"Pictured monuments in cities: ${regionMonuments.size}")

    val cityMonuments = regionMonuments.groupBy(m => cityShort(m.city.getOrElse("")))

    val links = cities.sorted.map { city =>

      val monuments = cityMonuments.getOrElse(city, Nil)

      var allImages = 0

      val galleries = monuments.map {
        m =>
          val images = (imageDb.getOrElse(m.id, Nil).map(_.title) ++ m.photo.toSeq).distinct
          allImages += images.size
          val gallery = org.scalawiki.dto.Image.gallery(images)

          s"""== ${m.name.replaceAll("\\[\\[", "[[:uk:")} ==
             |'''Рік:''' ${m.year.getOrElse("")}, '''Адреса:''' ${m.place.getOrElse("")}, '''Тип:''' ${m.typ.getOrElse("")},
             |'''Охоронний номер:''' ${m.stateId.getOrElse("")}\n""".stripMargin +
            gallery
      }
      val stat = s"пам'яток: ${monuments.size}, фото: $allImages"
      val text = stat + "\n" + galleries.mkString("\n")
      val title = page(city)

      println(s"City: $city, $stat, page: $title")

      ukWiki.page(title).edit(text)

      if (allImages > 0) {
        s"#[[${page(city)}|$city]] ($stat)"
      } else {
        s"#$city (немає фото)"
      }
    }

    val stat = s"міст: ${cities.size}, пам'яток: ${regionMonuments.size}"
    val list = stat + "\n" + links.mkString("\n")
    val title = s"User:Ilya/$regionName"
    Thread.sleep(1000)
    ukWiki.page(title).edit(list)

    s"#[[$title|$regionName]] ($stat)"
  }

}
