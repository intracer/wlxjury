package org.intracer.wmua

import controllers.GlobalRefactor
import db.scalikejdbc._
import org.intracer.wmua.cmd.{DistributeImages, ImageWithRatingSeqFilter}
import org.joda.time.DateTime
import org.scalawiki.MwBot
import org.scalawiki.dto.Namespace
import org.scalawiki.wlx.dto.{Contest, SpecialNomination}
import org.scalawiki.wlx.query.MonumentQuery
import play.api.Play
import scalikejdbc.{ConnectionPool, GlobalSettings, LoggingSQLAndTimeSettings}

import scala.concurrent.Await
import scala.io.Source
import scala.concurrent.ExecutionContext.Implicits.global
import controllers.Global.commons
import spray.util.pimpFuture

object Tools {

  val regions = Set("44")

  def main(args: Array[String]) {
    Class.forName("com.mysql.jdbc.Driver")
    val url: String = "jdbc:mysql://jury.wikilovesearth.org.ua/wlxjury"
    println(s"URL:" + url)

    val user = Play.current.configuration.getString("db.default.user").get
    val password = Play.current.configuration.getString("db.default.user").get

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
  }

  def distributeImages(
                        round: Round,
                        jurors: Seq[User],
                        prevRound: Option[Round],
                        includeRegionIds: Set[String] = Set.empty,
                        excludeRegionIds: Set[String] = Set.empty,
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
                      ) = {


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

    val contestId = round.contest
    val imagesAll = prevRound.fold[Seq[ImageWithRating]](
      ImageJdbc.findByContest(contestId).map(i =>
        new ImageWithRating(i, Seq.empty)
      )
    )(r =>
      ImageJdbc.byRoundMerged(r.id.get)
    )
    println("Total images: " + imagesAll.size)

    val funGens = ImageWithRatingSeqFilter.funGenerators(prevRound,
      includeRegionIds = includeRegionIds,
      excludeRegionIds = excludeRegionIds,
      includePageIds = includePageIds ++ includeFromCats.toSet,
      excludePageIds = excludePageIds ++ existingImageIds ++ excludeFromCats.toSet,
      includeTitles = includeTitles,
      excludeTitles = excludeTitles,
      includeJurorId = includeJurorId,
      excludeJurorId = excludeJurorId ++ existingJurorIds,
      selectMinAvgRating = prevRound.flatMap(_ => selectMinAvgRating),
      selectTopByRating = prevRound.flatMap(_ => selectTopByRating),
      selectedAtLeast = prevRound.flatMap(_ => selectedAtLeast),
      mpxAtLeast = mpxAtLeast
    )

    println("Image filters:")
    funGens.foreach(println)
    val filterChain = ImageWithRatingSeqFilter.makeFunChain(funGens)

    val images = filterChain(imagesAll).map(_.image)
    println("Images after filtering: " + images.size)

    DistributeImages(round, images, jurors).apply()
  }

  def insertMonumentsWLM() = {
    val wlmContest = Contest.WLMUkraine(2014, "09-15", "10-15")

    val monumentQuery = MonumentQuery.create(wlmContest)
    val allMonuments = monumentQuery.byMonumentTemplate(wlmContest.listTemplate.getOrElse(""), None)
    println(allMonuments.size)
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
    val contest = ContestJuryJdbc.find(77L).get

    globalRefactor.appendImages(contest.images.get, contest)

    val round = RoundJdbc.find(133L).get

    ImageDistributor.distributeImages(contest.id.get, round)
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
    val contest = ContestJuryJdbc.find(37L).get

    globalRefactor.appendImages("Commons:Wiki Loves Earth 2015/Winners", contest)
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

        val thisCountry = ImageJdbc.findByContest(77L).map(_.pageId).toSet

        val intersection = thisCountry intersect ids

        intersection.foreach {
          id => SelectionJdbc.setRound(id, 133L, 77L, 138L)
        }
    }
  }
}
