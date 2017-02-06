package controllers

import org.intracer.wmua.{ImageWithRating, Round, User}

object Csv {

  def exportRates(files: Seq[ImageWithRating], jurors: Seq[User], round: Round) = {

    val header = Seq("Rank", "File", "Overall") ++ jurors.map(_.fullname)

    val ranks = ImageWithRating.rankImages(files, round)
    val rows = for ((file, rank) <- files.zip(ranks))
      yield Seq(rank, file.image.title, file.rateString(round)) ++ jurors.map(file.jurorRateStr)

    header +: rows
  }
}
