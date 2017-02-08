package controllers

import java.io.{StringWriter, Writer}

import com.github.tototoshi.csv.CSVWriter
import org.intracer.wmua.{ImageWithRating, Round, User}

object Csv {

  def writeStringBuffer(data: Seq[Seq[Any]]): StringBuffer = {
    val writer = new StringWriter()
    write(data, writer)
    writer.getBuffer
  }

  def writeRow(data: Seq[Any]): String = {
    val writer = new StringWriter()
    val csv = CSVWriter.open(writer)
    csv.writeRow(data)
    csv.close()
    writer.getBuffer.toString
  }

  def write(data: Seq[Seq[Any]], writer: Writer): Unit = {
    val csv = CSVWriter.open(writer)
    csv.writeAll(data)
    csv.close()
  }

  def exportRates(files: Seq[ImageWithRating], jurors: Seq[User], round: Round): Seq[Seq[String]] = {
    val header = Seq("Rank", "File", "Overall") ++ jurors.map(_.fullname)

    val ranks = ImageWithRating.rankImages(files, round)
    val rows = for ((file, rank) <- files.zip(ranks))
      yield Seq(rank, file.image.title, file.rateString(round)) ++ jurors.map(file.jurorRateStr)

    header +: rows
  }

  def addBom(data: Seq[Seq[String]]): Seq[Seq[String]] = {
    val BOM = "\ufeff"
    val header = data.head
    val headerWithBom = (BOM + header.head) +: header.tail
    headerWithBom +: data.tail
  }
}
