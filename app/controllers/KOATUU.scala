package controllers

import java.util.Properties
import java.io.FileReader

import scala.collection.JavaConverters._
import org.intracer.wmua.Image


object KOATUU {
  final val REGION_PROPERTIES: String = "conf/KOATUU.properties"
  final val regionProperties: Properties = new Properties
  var regions: Map[String, String] = Map[String, String]()

  final val FILE_ID_PROPERTIES: String = "conf/fileIds.txt"
  final val fileIdProperties: Properties = new Properties
  var fileIds: Map[String, String] = Map[String, String]()

  var fileHashes: Map[String, String] = Map[String, String]()


  def load() {
    regionProperties.load(new FileReader(REGION_PROPERTIES))

    regions = regionProperties.asInstanceOf[java.util.Map[String, String]].asScala.toMap

    fileIdProperties.load(new FileReader(FILE_ID_PROPERTIES))
    fileIds = fileIdProperties.asInstanceOf[java.util.Map[String, String]].asScala.toMap

    fileHashes = fileIds.keys.map(f => f.hashCode.toString -> f).toMap

    if (fileIds.keys.size != fileHashes.keys.size) {
      val msg: String = s"${fileIds.keys.size} != ${fileHashes.keys.size}"
      throw new RuntimeException(msg)
    }


  }

  def regionList = regions.map(x => x).toSeq.sortBy(_._1).map(_._2)

  def regionIdByMonumentId(id: String): Option[String] = {
    val split = id.split("-")

    if (regions.contains(split(0))) {
      Some(split(0))
    } else {
      None
    }
  }

  def regionIdByFile(file: Image) = {
    fileIds.get(file.title).flatMap(regionIdByMonumentId)
  }

  def filesInRegion(files: Seq[Image], regionId: String) = {

    regionId match {
      case "all" => files
      case "no" => files.filter(f => !regionIdByFile(f).isDefined)
      case _ => files.filter(f => regionIdByFile(f).exists(fileRegion => fileRegion == regionId))
    }
  }

}