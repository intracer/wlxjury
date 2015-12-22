package org.intracer.wmua

import akka.actor.ActorSystem
import controllers.GlobalRefactor
import db.scalikejdbc._
import org.joda.time.DateTime
import org.scalawiki.{MwBot, MwBotImpl}
import org.scalawiki.dto.Namespace
import org.scalawiki.http.HttpClientImpl
import org.scalawiki.wlx.dto.{Contest, SpecialNomination}
import org.scalawiki.wlx.query.MonumentQuery
import scalikejdbc.{ConnectionPool, GlobalSettings, LoggingSQLAndTimeSettings}

import scala.concurrent.Await
import scala.io.Source

object Tools {

  val regions = Set("44")

  def main(args: Array[String]) {
    Class.forName("com.mysql.jdbc.Driver")
    val url: String = "jdbc:mysql://jury.wikilovesearth.org.ua/wlxjury"
    println(s"URL:" + url)

    ConnectionPool.singleton(url, "***REMOVED***", "***REMOVED***")
//    ConnectionPool.singleton("jdbc:mysql://localhost/wlxjury", "***REMOVED***", "***REMOVED***")

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

    //users()

    //    GlobalRefactor.addContestCategories("Wiki Loves Monuments", 2015)
    val contest = ContestJuryJdbc.find(77L).get

    // GlobalRefactor.rateByCategory("Category:Obviously ineligible submissions for WLM 2015 in Ukraine", 491, 89, -1)

    //val query = GlobalRefactor.commons.page(contest.images.get)
    //GlobalRefactor.updateMonuments(query, contest)
    //GlobalRefactor.distributeByCategory("Category:WLM 2015 in Ukraine Round Zero", contest.get)
//    addUsers(contest, 5)

//    addItalyR3()

//        val round = RoundJdbc.find(116)
//        ImageDistributor.distributeImages(contest.id.get, round.get)
//    fixRound()
     initImages()
    //verifySpain()
    //internationalRound()
    // val wlmContest = Contest.WLMUkraine(2014, "09-15", "10-15")
    //wooden(wlmContest)

    //    GlobalRefactor.initLists(wlmContest)
    //    return

    //    ConnectionPool.singleton("jdbc:mysql://localhost/wlxjury", "***REMOVED***", "***REMOVED***")

    //    for (contest <- ContestJury.findAll()) {
    //
    //      if (contest.country == "Ghana") {
    //        println(contest)

    //                controllers.GlobalRefactor.initContest("Category:Images from Wiki Loves Earth 2014 in " + contest.country,  contest)

//    roundAndUsers(contest)

    //updateResolution(contest)

    //Admin.distributeImages(contest, Round.findByContest(contest.id).head)
    //        createNextRound()
    //      }
    //    }
  }

  def roundAndUsers(contest: ContestJury) {
    val rounds = RoundJdbc.findByContest(contest.id.get)
    val newRoundNum = 2
    val round = if (rounds.size < newRoundNum) {
      val r = RoundJdbc.create(newRoundNum, Some(""), contest.id.get, "jury", 0, 10, Some(1), Some(1), None)
      ContestJuryJdbc.setCurrentRound(contest.id.get, r.id.get)
      r
    } else {
      rounds.find(_.number == newRoundNum).head
    }

    if (round.jurors.isEmpty) {
      for (i <- 1 to 10) {
        val login = contest.country.replaceAll("[ \\-\\&]", "")
        UserJdbc.create(
          "Test user " + contest.country + i,
          login + i + "@test", UserJdbc.sha1(contest.country + "/" + "123"),
          Set("jury"),
          contest.id.get,
          Some("en"))
      }
    }

    val jurors = round.jurors

    ContestJuryJdbc.setCurrentRound(contest.id.get, round.id.get)

    createNextRound(round, jurors, rounds.find(_.number == newRoundNum - 1).get)
  }

  def createNextRound(round: Round, jurors: Seq[User], prevRound: Round) = {
    val newImages = ImageJdbc.byRatingMerged(0, round.id.get)
    if (true || newImages.isEmpty) {

      //      val selectedRegions = Set("01", "07", "14", "21", "26", "44", "48", "74")
      //
//      val nofop = Set(43865979L, 43554164L, 43392817L, 43263295L, 43491572L
//      //  43229769L, 43229778L, 43229790L, 43229767L
//      )
//
//      val selectedIds = Set(42857615L, 43794877)

      val selection1 = Seq.empty

//        Set("File:เศียรพระพุทธรูปในรากโพธิ์.jpg",
//          "File:Leuchtturm in Westerheversand.jpg",
//          "File:Німецький Народний дім.jpg",
//          "File:Neues Rathaus Hannover, Innenansicht.jpg",
//          "File:Château de Chambord - 19-08-2015 - Arnaud Scherer.jpg",
//          "File:Vakil mosque Panorama.jpg",
//          "File:Jinnah Mausoleum.JPG",
//          "File:Samaan Monastery.jpg",
//          "File:44 Apollo in bosquet Fächer, gardens of Schönbrunn 03.jpg",
//          "File:Мереживо Пассажу.jpg",
//          "File:Biserica Calvaria de la Cluj-Mănăștur, vedere sud-vestică, 2014.JPG",
//          "File:Shah(Emam ) Mosque , Isfahan.jpg",
//          "File:Соловецкий монастырь.jpg",
//          "File:La Sacra ammantata dalla neve.jpg",
//          "File:Ayuntamiento, La Coruña, España, 2015-09-25, DD 141-143 HDR.jpg",
//          "File:Museu da inconfidencia.JPG",
//          "File:Monumento a Duque de Caxias.jpg",
//          "File:Wat phra mahathat woramahawihan nakhon si thammarat.jpg",
//          "File:Ghare Kelisa.jpg",
//          "File:Amphithéâtre d'El Jem.jpg",
//          "File:مسجد الرفاعى والسلطان حسن ومدفع رمضان بقلعه صلاح الدين.jpg",
//          "File:วัดศรีชุม20.JPG",
//          "File:Lord Bishnu-Shesh Narayan.JPG",
//          "File:Munkholmen fra Talerøret Trondheim.jpg",
//          "File:Ermita de la Virgen de la Peña, LIC Sierras de Santo Domingo y Caballera, Aniés, Huesca, España, 2015-01-06, DD 06-07 PAN.JPG",
//          "File:วัดมหาธาตุ 001.jpg",
//          "File:Kamianiec Podilsky Stary Zamek DSC 0829 68-104-9007.jpg",
//          "File:Opernpassage Panorama.jpg",
//          "File:Olympiastadion Berlin Innenansicht.jpg",
//          "File:Hook Head MG 0606 - Version 2.jpg",
//          "File:Замок \"Ласточкино гнездо\", Ялта, АР Крым.jpg",
//          "File:U995Steuerbordbug.JPG",
//          "File:Azadi Tower.jpg",
//          "File:Kilcrea Abbey.jpg",
//          "File:Clarion Hotel Post 2015 01.jpg",
//          "File:Nasir-al molk -1.jpg",
//          "File:Geghard...3.jpg")

      //val filtered = fromDb


    //  val ids = Set(43851006L, 43493278L, 43852452L, 43866154L, 43852906L)
      val existingIds = newImages.map(_.pageId).toSet

      val imagesAll = ImageJdbc.byRoundMerged(prevRound.id.get).toArray.sortBy(-_.totalRate(prevRound)).take(39).filter(i => selection1.contains(i.image.title))

      //.filter(i => ids.contains(i.image.pageId))
      // ImageJdbc.byRatingMerged(1, prevRound.id.get).toArray
//      val inRegion = imagesAll.filter(_.image.region.exists(regions.contains))
      val images = imagesAll//.filterNot(i => existingIds.contains(i.pageId))

      //.filter(_.rateSum >= 2)
      //.filter(i => selectedIds.contains(i.image.pageId))
      //.filterNot(i => nofop.contains(i.image.pageId))
      //.filter(_.selection.head.juryId == 581L)
      //.filterNot(_.selection.head.juryId == 581L)
      //.filterNot(i => existingIds.contains(i.pageId))
            //  ImageJdbc.byRoundMerged(prevRound.id.get).sortBy(-_.totalRate(prevRound)).toArray.take(130)
      //.filter(_.image.region.exists(regions.contains)).sortBy(-_.totalRate)
      //      .toArray.take(29)

      //      ImageJdbc.findAll().filter(_.region == Some("44"))
//       val selected = //Seq("File:Torre de Hércules, La Coruña, España, 2015-09-25, DD 23-31 HDR.jpg")
//        Source.fromFile("porota 2 kolo najlepsie hodnotenie 6.0.txt")(scala.io.Codec.UTF8).getLines().map(_.replace(160.asInstanceOf[Char], ' ').trim).toSet
      val imagesByJurors = images//.filter(i => selected.contains(i.title)) /* .filter {
/*        iwr => iwr.selection.exists {
          s => jurors.exists(j => j.id.contains(s.juryId))
        }
      }*/
      //
      val selection = jurors.flatMap { juror =>
          imagesByJurors.map(img => new Selection(0, img.pageId, 0, juror.id.get, round.id.get, DateTime.now))
        }

      //      val images = ImageJdbc.byRoundMerged(prevRound.id.toInt).sortBy(-_.totalRate).take(21)
      //      val selection = images.flatMap(_.selection).map(_.copy(id = 0, round = round.id))

      SelectionJdbc.batchInsert(selection)
    }
  }

  def insertMonumentsWLM() = {
    val wlmContest = Contest.WLMUkraine(2014, "09-15", "10-15")

    val monumentQuery = MonumentQuery.create(wlmContest)
    val allMonuments = monumentQuery.byMonumentTemplate(wlmContest.listTemplate.getOrElse(""), None)
    println(allMonuments.size)
  }

  def updateResolution(contest: ContestJury) = {

    val system = ActorSystem()
    val http = new HttpClientImpl(system)

    import system.dispatcher

    val commons = new MwBotImpl(http, system, controllers.Global.COMMONS_WIKIMEDIA_ORG, None)

    import scala.concurrent.duration._

    Await.result(commons.login("***REMOVED***", "***REMOVED***"), 1.minute)

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

  def users() = {
    for (i <- 1 to 12) {
      val password = UserJdbc.randomString(8)
      val hash = UserJdbc.sha1("Ukraine/" + password)

      println(s"pw: $password, hash: $hash")

    }

  }

  def globalRefactor = {
    val commons = MwBot.get(MwBot.commons)
    new GlobalRefactor(commons)
  }

  def initImages(): Unit = {

    val contest = ContestJuryJdbc.find(77L).get


    //    val category: String = "User:***REMOVED***/files" // "Commons:Wiki Loves Earth 2014/Finalists"
    globalRefactor.appendImages(contest.images.get, contest)

    val prevRouTond = RoundJdbc.find(133L).get
    val round = RoundJdbc.find(133L).get

    //    val selection = Selection.byRound(22L)

    ImageDistributor.distributeImages(contest.id.get, round)

//    val jurors = round.jurors.filter(j => j.id.get == 626)
//    createNextRound(round, jurors, prevRound)
  }

  def wooden(wlmContest: Contest) = {
    val page = "Commons:Images from Wiki Loves Monuments 2014 in Ukraine special nomination Пам'ятки дерев'яної архітектури України"
    val monumentQuery = MonumentQuery.create(wlmContest)
    val nomination = SpecialNomination.wooden
    val monumentsListsId = monumentQuery.byPage(nomination.pages.head, nomination.listTemplate).map(_.id).toSet
    val nanaRound = RoundJdbc.find(27L).get
    val monumentIdsByNana = ImageJdbc.byRatingMerged(1, nanaRound.id.get).flatMap(_.image.monumentId).toSet

    val onlyNana = monumentIdsByNana -- monumentsListsId

    val allIds = monumentIdsByNana ++ monumentsListsId

    val category = "Category:Images from Wiki Loves Monuments 2014 in Ukraine"
    val contest = ContestJuryJdbc.find(20L).get
    globalRefactor.appendImages(category, contest, allIds)

  }

  def internationalRound() = {
    //GlobalRefactor.commons.page("Commons:Wiki Loves Earth 2015/Winners").
    val contest = ContestJuryJdbc.find(37L).get

    globalRefactor.appendImages("Commons:Wiki Loves Earth 2015/Winners", contest)
  }

  def addUsers(contest: ContestJury, number: Int) = {
    val country = contest.country.replaceAll("[ \\-\\&]", "")
    val jurors = (1 to number).map(i => country + "ESPCJuror"  + i)

    val orgCom = (1 to 1).map(i => country + "ESPCOrgCom" )
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
          contest.id.get,
          Some("en"))
    }

//    val round = Round(None, 1, Some("Round 1"), contest.id.get, distribution = 0, active = true, rates = Round.ratesById(1))
//
//    val roundId = RoundJdbc.create(round).id
//    ContestJuryJdbc.setCurrentRound(round.contest, roundId.get)
//
//    val dbRound = round.copy(id = roundId)

       //     val dbRound = RoundJdbc.find().get

    logins.zip(passwords).foreach {
      case (login, password) =>
        println(s"$login / $password")
    }

     //   ImageDistributor.distributeImages(contest, dbRound)
    //
    //    logins.zip(passwords).foreach {
    //      case (login, password) =>
    //        println(s"$login / $password")
    //    }
  }


  def verifySpain(): Unit = {
    val newImages = ImageJdbc.byRatingMerged(0, 112).map(_.title).toSet
    val selected = Source.fromFile("spain.txt")(scala.io.Codec.UTF8).getLines().map(_.replace(160.asInstanceOf[Char], ' ').trim).toSet

    val absent = selected -- newImages

    absent.foreach(println)
  }

  def addItalyR3() = {
    val images = ImageJdbc.byRoundMerged(106).map(_.image).toArray

    val mapped = images.map(i => i.copy(pageId = i.pageId + 169660173900L))

    ImageJdbc.batchInsert(mapped)
  }

  def makeGuest() = {
    val prevRound = RoundJdbc.find(89).get
    val currentRound = RoundJdbc.find(104).get

    val unrated = ImageJdbc.byRatingMerged(0, prevRound.id.get).toArray
    val rejected = ImageJdbc.byRatingMerged(-1, prevRound.id.get).toArray
    val all = unrated ++ rejected

    val juror = UserJdbc.find(581).get

    val selection =
      all.map(img => new Selection(0, img.pageId, 0, juror.id.get, currentRound.id.get, DateTime.now))

    SelectionJdbc.batchInsert(selection)
  }

  def removeIneligible() = {
    val system = ActorSystem()
    import system.dispatcher

    val category = "Category:Obviously ineligible submissions for ESPC 2015 in Ukraine"
    val query = globalRefactor.commons.page(category)

    query.imageInfoByGenerator("categorymembers", "cm", Set(Namespace.FILE)).map {
      filesInCategory =>
        val ids = filesInCategory.flatMap(_.id)

        ids.foreach {
          id => SelectionJdbc.destroyAll(id)
        }
    }
  }

  def fixRound() = {
    val system = ActorSystem()
    import system.dispatcher

    val category = "Category:Non-photographic media from European Science Photo Competition 2015"
    val query = globalRefactor.commons.page(category)

    query.imageInfoByGenerator("categorymembers", "cm", Set(Namespace.FILE)).map {
      filesInCategory =>
        val ids = filesInCategory.flatMap(_.id).toSet

        val thisCountry = ImageJdbc.findByContest(77L).map(_.pageId).toSet

        val intersection = thisCountry intersect ids

        intersection.foreach {
          id => SelectionJdbc.setRound(id, 133L, 77L, 138L)
        }
    }
  }


}
