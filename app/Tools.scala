import controllers.Admin
import org.intracer.wmua.{User, Contest, Round}
import scalikejdbc.ConnectionPool

object Tools {

  //  db.default.driver=com.mysql.jdbc.Driver
  //  db.default.url="jdbc:mysql://localhost/wlxjury"
  //  db.default.user="***REMOVED***"
  //  db.default.password="***REMOVED***"


  def main(args: Array[String]) {
    Class.forName("com.mysql.jdbc.Driver")
    ConnectionPool.singleton("jdbc:mysql://localhost/wlxjury", "***REMOVED***", "***REMOVED***")

    for (contest <- Contest.findAll()) {
      println(contest)

      if (contest.country != "Ukraine") {

        roundAndUsers(contest)

        Admin.distributeImages(contest)
      }
    }
  }

  def roundAndUsers(contest: Contest) {
    val rounds = Round.findByContest(contest.id)
    val round = if (rounds.isEmpty) {
      val r = Round.create(1, Some("Test " + contest.country), contest.id.toInt, "jury", 2, 1, 1, 1, None)
      Contest.setCurrentRound(contest.id.toInt, r.id.toInt)
      r
    } else {
      rounds.head
    }

    val jurors = round.jurors

    if (jurors.isEmpty) {
      for (i <- 1 to 10) {
        val login = contest.country.replaceAll("[ \\-\\&]", "")
        User.create("Test user " + contest.country + i, login + i + "@test", User.sha1(contest.country + "/" +"123"), Set("jury"), contest.id.toInt, Some("en"))
      }
    }

  }

}
