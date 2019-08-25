package controllers

import db.scalikejdbc._
import org.intracer.wmua._
import org.intracer.wmua.cmd.{FetchImageInfo, ImageEnricher, FetchImageText}
import org.scalawiki.MwBot
import org.scalawiki.dto.cmd.Action
import org.scalawiki.dto.cmd.query.list.ListArgs
import org.scalawiki.dto.cmd.query.{Generator, Query}
import org.scalawiki.dto.{Namespace, Page}
import org.scalawiki.query.DslQuery

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

    ContestJuryJdbc.setImagesSource(contest.getId, Some(source))

    val existingImages = ImageJdbc.findByContest(contest)

    initImagesFromSource(contest, source, imageList, existingImages, idsFilter, max)
  }

  def createJury() {
    val selection = SelectionJdbc.findAll()
    if (selection.isEmpty) {
    }
  }

  def initImagesFromSource(contest: ContestJury,
                           source: String,
                           titles: String,
                           existing: Seq[Image],
                           idsFilter: Set[String] = Set.empty,
                           max: Long) = {
    val existingByPageId = existing.groupBy(_.pageId)

    val withImageDescriptions = contest.monumentIdTemplate.isDefined || contest.country.toLowerCase == "international"

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

    val result = getImages.map { images =>

      val newImages =  images.filter(image => !existingByPageId.contains(image.pageId))

      val existingIds = ImageJdbc.existingIds(newImages.map(_.pageId).toSet).toSet

      val notInOtherContests = newImages.filterNot(image => existingIds.contains(image.pageId))

      val categoryId = CategoryJdbc.findOrInsert(source)
      saveNewImages(contest, notInOtherContests)
      CategoryLinkJdbc.addToCategory(categoryId, newImages)

      val updatedImages =  images.filter(image => existingByPageId.contains(image.pageId) && existingByPageId(image.pageId) != image)
      updatedImages.foreach(ImageJdbc.update)
    }

    Await.result(result, 500.minutes)
  }

  def fetchImageDescriptions(contest: ContestJury, source: String, max: Long, imageInfos: Future[Seq[Image]]): Future[Seq[Image]] = {
    val revInfo = FetchImageText(source, contest, contest.monumentIdTemplate, commons, max).apply()
    ImageEnricher.zipWithRevData(imageInfos, revInfo)
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

    //    val round = Round(None, 1, Some("Round 1"), contest.getId, distribution = 0, active = true)
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

                val selection = filesInCategory.map(img => Selection(img, juror, round))
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