package db

import org.intracer.wmua.ContestJury

import scala.concurrent.Future

trait ContestJuryDao {

  def currentRound(id: Int): Future[Int]

  def byCountry: Future[Map[String, Seq[ContestJury]]]

  def byId(id: Int): Future[ContestJury]

  def find(id: Long): Future[ContestJury]

  def findAll(): Future[Seq[ContestJury]]

  def countAll(): Future[Int]

  def updateImages(id: Long, images: Option[String]): Future[Int]

  def setCurrentRound(id: Int, round: Int): Future[Int]

}
