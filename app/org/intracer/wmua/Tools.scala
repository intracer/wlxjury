package org.intracer.wmua

import akka.actor.ActorSystem
import client.dto.Namespace
import client.{HttpClientImpl, MwBot}
import controllers.Admin
import org.joda.time.DateTime
import scalikejdbc.ConnectionPool

import scala.concurrent.Await

object Tools {

  //  db.default.driver=com.mysql.jdbc.Driver
  //  db.default.url="jdbc:mysql://localhost/wlxjury"
  //  db.default.user="***REMOVED***"
  //  db.default.password="***REMOVED***"


  def main(args: Array[String]) {
    Class.forName("com.mysql.jdbc.Driver")
        ConnectionPool.singleton("jdbc:mysql://jury.wikilovesearth.org.ua/wlxjury", "***REMOVED***", "***REMOVED***")
//    ConnectionPool.singleton("jdbc:mysql://localhost/wlxjury", "***REMOVED***", "***REMOVED***")

    for (contest <- ContestJury.findAll()) {

      if (contest.country == "Ghana") {
        println(contest)

//        controllers.GlobalRefactor.initContest("Category:Images from Wiki Loves Earth 2014 in " + contest.country,  contest)

        //roundAndUsers(contest)

        updateResolution(contest)

        Admin.distributeImages(contest, Round.findByContest(contest.id).head)
  //      createNextRound()
      }
    }
  }

  def roundAndUsers(contest: ContestJury) {
    val rounds = Round.findByContest(contest.id)
    val newRoundNum = 2
    val round = if (rounds.size < newRoundNum) {
      val r = Round.create(newRoundNum, Some(""), contest.id.toInt, "jury", 0, 1, Some(1), Some(1), None)
      ContestJury.setCurrentRound(contest.id.toInt, r.id.toInt)
      r
    } else {
      rounds.find(_.number == newRoundNum).head
    }

    if (round.jurors.isEmpty) {
      for (i <- 1 to 10) {
        val login = contest.country.replaceAll("[ \\-\\&]", "")
        User.create("Test user " + contest.country + i, login + i + "@test", User.sha1(contest.country + "/" + "123"), Set("jury"), contest.id.toInt, Some("en"))
      }
    }

    val jurors = round.jurors

    //    Contest.setCurrentRound(contest.id.toInt, round.id.toInt)

    createNextRound(round, jurors, rounds.find(_.number == newRoundNum - 1).get)
  }

  def createNextRound(round: Round, jurors: Seq[User], prevRound: Round) = {
    val newImages = ImageJdbc.byRatingMerged(0, round.id.toInt)
    if (true || newImages.isEmpty) {

//      val selectedRegions = Set("01", "07", "14", "21", "26", "44", "48", "74")
//
      val images = ImageJdbc.byRatingMerged(0, prevRound.id.toInt)
//      ImageJdbc.findAll().filter(_.region == Some("44"))
//
      val selection = jurors.flatMap { juror =>
      images.map(img => new Selection(0, img.pageId, 0, juror.id, round.id, DateTime.now))
    }

//      val images = ImageJdbc.byRoundMerged(prevRound.id.toInt).sortBy(-_.totalRate).take(21)
//      val selection = images.flatMap(_.selection).map(_.copy(id = 0, round = round.id))

      Selection.batchInsert(selection)


    }
  }

  def updateResolution(contest: ContestJury) = {

    val system = ActorSystem()
    val http = new HttpClientImpl(system)

    import system.dispatcher

    val commons = new MwBot(http, system, controllers.Global.COMMONS_WIKIMEDIA_ORG)
    import scala.concurrent.duration._
    Await.result(commons.login("***REMOVED***", "***REMOVED***"), 1.minute)

    val category = "Category:Images from Wiki Loves Earth 2014 in Ghana"
    val query = commons.page(category)

    query.imageInfoByGenerator("categorymembers", "cm", Set(Namespace.FILE_NAMESPACE)).map {
      filesInCategory =>
        val newImages = filesInCategory.flatMap(page => ImageJdbc.fromPage(page, contest)).groupBy(_.pageId)
        val existing = ImageJdbc.findAll().toSet

        for (i1 <- existing; i2 <- newImages.get(i1.pageId).map(seq => seq.head)
        if i1.width != i2.width || i1.height != i2.height) {
          println(s"${i2.pageId} ${i1.title}  ${i1.width}x${i1.height} -> ${i2.width}x${i2.height}")
          ImageJdbc.updateResolution(i1.pageId, i2.width, i2.height)
        }
    }
  }

}
