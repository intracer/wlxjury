package controllers

import org.intracer.wmua._
import org.specs2.mutable.Specification

class CsvSpec extends Specification {

  val binaryRound = Round(None, 1, Some("br"), 0, rates = Round.binaryRound)
  def ratedRound(maxRate: Int) = Round(None, 1, Some("rr"), 0, rates = Round.ratesById(maxRate))

  "export rates" should {
    "export nothing" in {
      Gallery.exportRates(Seq.empty, Seq.empty, binaryRound) === Seq(Seq("Rank", "File", "Overall"))
    }

    "export unrated file, no jurors" in {
      val file = ImageWithRating(Image(0, 0, "File:1.jpg"), Seq.empty)
      Gallery.exportRates(Seq(file), Seq.empty, binaryRound) === Seq(
        Seq("Rank", "File", "Overall"),
        Seq("1", "File:1.jpg", "0")
      )
    }

    "export two unrated files, no jurors" in {
      val files = (1 to 2).map(i => ImageWithRating(Image(0, 0, s"File:$i.jpg"), Seq.empty))
      Gallery.exportRates(files, Seq.empty, binaryRound) === Seq(
        Seq("Rank", "File", "Overall"),
        Seq("1-2", "File:1.jpg", "0"),
        Seq("1-2", "File:2.jpg", "0")
      )
    }

    "export 1 file, 1 juror, binary round" in {
      val files = Seq(ImageWithRating(Image(0, 0, "File:a.jpg"), Seq(Selection(0, 0, 1, 9, 0))))
      Gallery.exportRates(files, Seq(User("Juror1", "", Some(9))), binaryRound) === Seq(
        Seq("Rank", "File", "Overall", "Juror1"),
        Seq("1", "File:a.jpg", "1", "1")
      )
    }

    "export 1 file, 1 juror, rated round" in {
      val files = Seq(ImageWithRating(Image(0, 0, "File:a.jpg"), Seq(Selection(0, 0, 5, 9, 0))))
      Gallery.exportRates(files, Seq(User("Juror1", "", Some(9))), ratedRound(10)) === Seq(
        Seq("Rank", "File", "Overall", "Juror1"),
        Seq("1", "File:a.jpg", "5.00 (5 / 1)", "5")
      )
    }
  }
}