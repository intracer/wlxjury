import org.intracer.wmua._
import org.joda.time.DateTime
import scalikejdbc.ConnectionPool

object Tools {

  //  db.default.driver=com.mysql.jdbc.Driver
  //  db.default.url="jdbc:mysql://localhost/wlxjury"
  //  db.default.user="***REMOVED***"
  //  db.default.password="***REMOVED***"


  def main(args: Array[String]) {
    Class.forName("com.mysql.jdbc.Driver")
    //    ConnectionPool.singleton("jdbc:mysql://jury.wikilovesearth.org.ua/wlxjury", "***REMOVED***", "***REMOVED***")
    ConnectionPool.singleton("jdbc:mysql://localhost/wlxjury", "***REMOVED***", "***REMOVED***")

    for (contest <- Contest.findAll()) {
      println(contest)

      if (contest.country == "Ukraine") {

        roundAndUsers(contest)

        //Admin.distributeImages(contest)
        //createNextRound()
      }
    }
  }

  def roundAndUsers(contest: Contest) {
    val rounds = Round.findByContest(contest.id)
    val newRoundNum = 5
    val round = if (rounds.size < newRoundNum) {
      val r = Round.create(newRoundNum, Some(""), contest.id.toInt, "jury", 0, 1, 1, 1, None)
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
    if (newImages.isEmpty) {

      val selectedRegions = Set("01", "07", "14", "21", "26", "48", "74")

      val images = Image.byRoundMerged(prevRound.id.toInt).filter(_.image.region.exists(r => !selectedRegions.contains(r)))

//      val selection = images.flatMap(_.selection).map(_.copy(id = 0, round = round.id))

      val selection = jurors.flatMap { juror =>
        images.map(img => new Selection(0, img.pageId, 0, juror.id, round.id, DateTime.now))
      }
      Selection.batchInsert(selection)
    }
  }

}
