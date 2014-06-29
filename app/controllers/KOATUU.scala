package controllers

import java.io.FileReader
import java.util.Properties

import scala.collection.JavaConverters._


object KOATUU {
  final val REGION_PROPERTIES: String = "conf/KOATUU.properties"
  final val regionProperties: Properties = new Properties
  var regions: Map[String, String] = Map[String, String]()

//  val regionPropertiesLocal = Map[String, Properties]

  def load() {
    regionProperties.load(new FileReader(REGION_PROPERTIES))

    regions = regionProperties.asInstanceOf[java.util.Map[String, String]].asScala.toMap
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

//  def regionIdByFile(file: ImageWithRating) = {
//    fileIds.get(file.title).flatMap(regionIdByMonumentId)
//  }
//
//  def filesInRegion(files: Seq[ImageWithRating], regionId: String) = {
//
//    regionId match {
//      case "all" => files
//      case "no" => files.filter(f => !regionIdByFile(f).isDefined)
//      case _ => files.filter(f => regionIdByFile(f).exists(fileRegion => fileRegion == regionId))
//    }
//  }

}