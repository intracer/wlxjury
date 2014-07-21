import akka.actor.ActorSystem
import client.dto.{Namespace, PageQuery}
import client.{HttpClientImpl, MwBot}
import org.intracer.wmua._
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

    for (contest <- Contest.findAll()) {

      if (contest.country == "Ukraine") {
        println(contest)

        //roundAndUsers(contest)

        updateResolution(contest)

        //Admin.distributeImages(contest)
        //createNextRound()
      }
    }
  }

  def roundAndUsers(contest: Contest) {
    val rounds = Round.findByContest(contest.id)
    val newRoundNum = 5
    val round = if (rounds.size < newRoundNum) {
      val r = Round.create(newRoundNum, Some(""), contest.id.toInt, "jury", 0, 1, Some(1), Some(1), None)
      Contest.setCurrentRound(contest.id.toInt, r.id.toInt)
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

    createNextRound(round, jurors, rounds.find(_.number == 2).get)
  }

  def createNextRound(round: Round, jurors: Seq[User], prevRound: Round) = {
    val newImages = Image.byRatingMerged(1, round.id.toInt)
    if (false && newImages.isEmpty) {

//      val selectedRegions = Set("01", "07", "14", "21", "26", "44", "48", "74")
//
      val images =
  //Image.byRoundMerged(prevRound.id.toInt).filter(_.image.region.exists(r => !selectedRegions.contains(r))) ++
      Image.findAll().filter(_.region == Some("44"))
//
      val selection = jurors.flatMap { juror =>
      images.map(img => new Selection(0, img.pageId, 0, juror.id, round.id, DateTime.now))
    }

//      val images = Image.byRoundMerged(prevRound.id.toInt).sortBy(-_.totalRate).take(21)
//      val selection = images.flatMap(_.selection).map(_.copy(id = 0, round = round.id))

      Selection.batchInsert(selection)


    }
  }

  def updateResolution(contest: Contest) = {

    val system = ActorSystem()
    val http = new HttpClientImpl(system)

    import system.dispatcher

    val commons = new MwBot(http, system, controllers.Global.COMMONS_WIKIMEDIA_ORG)
    import scala.concurrent.duration._
    Await.result(commons.login("***REMOVED***", "***REMOVED***"), 1.minute)

    val category = "Category:Images from Wiki Loves Earth 2014 in Ukraine"
    val query = PageQuery.byTitle(category)

    commons.imageInfoByGenerator("categorymembers", "cm", query, Set(Namespace.FILE_NAMESPACE)).map {
      filesInCategory =>
        val newImages = filesInCategory.flatMap(page => Image.fromPage(page, contest)).groupBy(_.pageId)
        val existing = Image.findAll().toSet

        for (i1 <- existing; i2 <- newImages.get(i1.pageId).map(seq => seq.head)
        if i1.width != i2.width || i1.height != i2.height) {
          println(s"${i2.pageId} ${i1.title}  ${i1.width}x${i1.height} -> ${i2.width}x${i2.height}")
          Image.updateResolution(i1.pageId, i2.width, i2.height)
        }
    }
  }

}
