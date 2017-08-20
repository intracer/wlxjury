package controllers

import db.scalikejdbc._
import org.intracer.wmua._
import org.intracer.wmua.cmd.{ImageEnricher, FetchImageInfo, ImageTextFromCategory}
import org.joda.time.DateTime
import org.scalawiki.MwBot
import org.scalawiki.dto.cmd.Action
import org.scalawiki.dto.cmd.query.list.ListArgs
import org.scalawiki.dto.cmd.query.{Generator, Query}
import org.scalawiki.dto.{Namespace, Page}
import org.scalawiki.query.{DslQuery, SinglePageQuery}
import org.scalawiki.wikitext.TemplateParser
import org.scalawiki.wlx.dto.Contest
import org.scalawiki.wlx.query.MonumentQuery

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class GlobalRefactor(val commons: MwBot) {

  import scala.concurrent.ExecutionContext.Implicits.global

  def initContest(category: String, contest: ContestJury): Any = {
    val images = ImageJdbc.findByContest(contest)

    if (images.isEmpty) {
      initImagesFromSource(contest, category, "", Seq.empty, max = 0)
    } else {
      //        initContestFiles(contest, images)
      //createJury()
    }
  }

  def appendImages(source: String, imageList: String, contest: ContestJury, idsFilter: Set[String] = Set.empty, max: Long = 0) = {
    val existingImages = ImageJdbc.findByContest(contest)

    initImagesFromSource(contest, source, imageList, existingImages, idsFilter, max)
  }

  def createJury() {
    val selection = SelectionJdbc.findAll()
    if (selection.isEmpty) {
    }
  }

  def initLists(contest: Contest) = {

    if (MonumentJdbc.findAll().isEmpty) {
      val ukWiki = MwBot.fromHost("uk.wikipedia.org")

      //    listsNew(system, http, ukWiki)

      val monumentQuery = MonumentQuery.create(contest)
      val monuments = monumentQuery.byMonumentTemplate()

      val fromDb = MonumentJdbc.findAll()
      val inDbIds = fromDb.map(_.id).toSet

      val newMonuments = monuments.filterNot(m => inDbIds.contains(m.id))

      MonumentJdbc.batchInsert(newMonuments)
    }

  }


  def initImagesFromSource(contest: ContestJury,
                           source: String,
                           titles: String,
                           existing: Seq[Image],
                           idsFilter: Set[String] = Set.empty,
                           max: Long) = {
    val existingPageIds = existing.map(_.pageId).toSet

    val withImageDescriptions = contest.monumentIdTemplate.isDefined

    val titlesSeq: Seq[String] = if (titles.trim.isEmpty)
      Seq.empty
    else
      titles.split("(\r\n|\n|\r)")

    val imageInfos = FetchImageInfo(source, titlesSeq, contest, commons, max).apply()

    val getImages = if (withImageDescriptions) {
      fetchImageDescriptions(contest, source, max, imageInfos)
    } else {
      imageInfos
    }

    val result = for (images <- getImages
      .map(_.filter(image => !existingPageIds.contains(image.pageId)))
    ) yield {
      val categoryId = CategoryJdbc.findOrInsert(source)
      saveNewImages(contest, images)
      CategoryLinkJdbc.addToCategory(categoryId, images)
    }

    Await.result(result, 5.minutes)
  }

  def fetchImageDescriptions(contest: ContestJury, source: String, max: Long, imageInfos: Future[Seq[Image]]): Future[Seq[Image]] = {
    val revInfo = ImageTextFromCategory(source, contest, contest.monumentIdTemplate, commons, max).apply()
    ImageEnricher.zipWithRevData(imageInfos, revInfo)
  }

  def updateMonuments(query: SinglePageQuery, contest: ContestJury) = {
    val monumentIdTemplate = contest.monumentIdTemplate.get

    query.revisionsByGenerator("categorymembers", "cm",
      Set.empty, Set("content", "timestamp", "user", "comment"), limit = "50", titlePrefix = None) map {
      pages =>

        val idRegex = """(\d\d)-(\d\d\d)-(\d\d\d\d)"""

        pages.foreach { page =>
          for (monumentId <- page.text.flatMap(text => defaultParam(text, monumentIdTemplate))
            .flatMap(id => if (id.matches(idRegex)) Some(id) else None);
               pageId <- page.id) {
            ImageJdbc.updateMonumentId(pageId, monumentId)
          }
        }
    } recover { case e: Exception => println(e) }
  }

  def defaultParam(text: String, templateName: String): Option[String] =
    TemplateParser.parseOne(text, Some(templateName)).flatMap(_.getParamOpt("1"))

  def namedParam(text: String, templateName: String, paramName: String): Option[String] =
    TemplateParser.parseOne(text, Some(templateName)).flatMap(_.getParamOpt(paramName))

  def descriptions(pages: Seq[Page], existingPageIds: Set[Long]): Seq[String] = {
    pages.sortBy(_.id).filterNot(i => existingPageIds.contains(i.id.get)).map {
      page =>
        page.text.flatMap(text => namedParam(text, "Information", "description")).getOrElse("")
    }
  }

  def saveNewImages(contest: ContestJury, imagesWithIds: Seq[Image]) = {
    println("saving images: " + imagesWithIds.size)

    ImageJdbc.batchInsert(imagesWithIds)
    println("saved images")
    //createJury()
    //    initContestFiles(contest, imagesWithIds)
  }

  def initUrls() {
    KOATUU.load()
  }

  def getCategories(parent: String): Future[Seq[Page]] = {

    val generatorArg = ListArgs.toDsl("categorymembers", Some(parent), None, Set(Namespace.CATEGORY), Some("max")).get
    val action = Action(Query(
      Generator(generatorArg)
    ))
    new DslQuery(action, commons).run()
  }

  def addContestCategories(contestName: String, year: Int) = {
    val parent = s"Category:Images from $contestName $year"
    getCategories(parent).map {
      categories =>
        val contests = categoriesToContests(contestName, year, parent, categories)
        //        val ids = ContestJuryJdbc.batchInsert(contests)
        //        val rounds =  ids.map (id => Round(None, 1, Some("Round 1"), id))
        //
        //        rounds.foreach {
        //          round =>
        //            val roundId = RoundJdbc.create(round).id
        //            ContestJuryJdbc.setCurrentRound(round.contest, roundId.get)
        //        }

        contests.foreach {
          contest =>
            val dbContest = ContestJuryJdbc.where('country -> contest.country, 'year -> year, 'name -> contestName).apply().head
            //GlobalRefactor.appendImages(contest.images.get, dbContest)
            addAdmin(dbContest)
        }
    }
  }

  def rateByCategory(category: String, juryId: Long, roundId: Long, rate: Int) = {

    val future = commons.page(category).imageInfoByGenerator("categorymembers", "cm",
      props = Set("timestamp", "user", "size", "url"),
      titlePrefix = None)

    future.map {
      filesInCategory =>

        filesInCategory.foreach {
          page =>
            SelectionJdbc.rate(page.id.get, juryId, roundId, rate)
        }
    }
  }

  def distributeByCategory(parent: String, contest: ContestJury) = {

    //    val round = Round(None, 1, Some("Round 1"), contest.id.get, distribution = 0, active = true)
    //
    //    val roundId = RoundJdbc.create(round).id
    //    ContestJuryJdbc.setCurrentRound(round.contest, roundId.get)

    val round = RoundJdbc.findById(89).get

    getCategories(parent).map {
      categories =>

        val jurors = categories.map("WLMUA2015_" + _.title.split("-")(1).trim.replaceAll(" ", "_"))
        val logins = jurors ++ Seq(contest.name + "OrgCom")
        val passwords = logins.map(s => UserJdbc.randomString(8)) // !! i =>
      val users = logins.zip(passwords).map {
        case (login, password) =>
          UserJdbc.create(
            login,
            login, UserJdbc.sha1(contest.country + "/" + password),
            if //(login.contains("Jury"))
            (jurors.contains(login))
              Set("jury")
            else Set("organizer"),
            contest.id,
            Some("uk"))
      }

        logins.zip(passwords).foreach {
          case (login, password) =>
            println(s"$login / $password")
        }

        val jurorsDb = users.init

        categories.zip(jurorsDb).map {
          case (category, juror) =>

            val future = commons.page(category.title).imageInfoByGenerator("categorymembers", "cm",
              props = Set("timestamp", "user", "size", "url"),
              titlePrefix = None)

            future.map {
              filesInCategory =>

                val selection = filesInCategory.map(img => new Selection(0, img.id.get, 0, juror.id.get, round.id.get, DateTime.now))
                SelectionJdbc.batchInsert(selection)
            }
        }
    }
  }

  def addAdmin(contest: ContestJury): Unit = {
    val shortContest = contest.name.split(" ").map(_.head).mkString("")
    val shortCountry = contest.country.replaceFirst("the ", "").replaceFirst(" & Nagorno-Karabakh", "").split(" ").mkString("")

    val name = shortContest + contest.year + shortCountry + "Admin"

    val password = UserJdbc.randomString(8)
    val hash = UserJdbc.sha1(contest.country + "/" + password)
    val user = User(name, name, None, Set("admin"), Some(hash), contest.id, Some("en"))

    println(s"admin user: $name / $password")
    UserJdbc.create(user)
  }

  def categoriesToContests(contest: String, year: Int, parent: String, categories: Seq[Page]): Seq[ContestJury] = {

    val imageCategories = categories.filter(_.title.startsWith(parent + " in "))
    val contests = imageCategories.map {
      imageCategory =>
        val title = imageCategory.title
        val country = title.replaceFirst(parent + " in ", "")
        ContestJury(None, contest, year, country, Some(title), None, None)
    }
    contests
  }
}