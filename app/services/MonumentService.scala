package services

import db.scalikejdbc.MonumentJdbc
import org.scalawiki.wlx.{ImageDB, MonumentDB}
import org.scalawiki.wlx.dto.{Contest, ContestType, Monument, SpecialNomination}
import org.scalawiki.wlx.query.MonumentQuery
import org.scalawiki.wlx.stat.ContestStat
import play.api.Logging

class MonumentService extends Logging {

  def updateLists(contest: Contest): Unit = {
    val monumentQuery = MonumentQuery.create(contest)
    val monuments = monumentQuery.byMonumentTemplate()
    val monumentIds = monuments.map(_.id).toSet

    val stat =
      ContestStat(
        contest,
        contest.year,
        Some(new MonumentDB(contest, monuments.toSeq)),
        new ImageDB(contest, Nil),
        new ImageDB(contest, Nil)
      )
    val specialNominationMonuments =
      if (contest.contestType == ContestType.WLM) {
        SpecialNomination
          .getMonumentsMap(SpecialNomination.nominations, stat)
          .values
          .flatten
          .filterNot(m => monumentIds.contains(m.id))
      } else Nil

    def truncate(
        monument: Monument,
        field: String,
        max: Int,
        copy: String => Monument
    ): Monument = {
      if (field.length > max) {
        copy(field.substring(0, max))
      } else monument
    }

    def truncOpt(
        monument: Monument,
        field: Option[String],
        max: Int,
        copy: Option[String] => Monument
    ): Monument = {
      if (field.exists(_.length > max)) {
        copy(field.map(_.substring(0, max)))
      } else monument
    }

    val allValidMonuments = (monuments ++ specialNominationMonuments).view
      .filter(_.id.matches("\\d{2}-\\d{3}-\\d{4}"))
      .map(m => truncate(m, m.name, 512, s => m.copy(name = s)))
      .map(m => truncOpt(m, m.typ, 255, s => m.copy(typ = s)))
      .map(m => truncOpt(m, m.subType, 255, s => m.copy(subType = s)))
      .map(m => truncOpt(m, m.year, 255, s => m.copy(year = s)))
      .map(m => truncOpt(m, m.city, 255, s => m.copy(city = s)))
      .toSeq

    logger.info(
      s"Updating monuments, " +
        s"from lists: ${monuments.size}, " +
        s"special nominations: ${specialNominationMonuments.size}," +
        s"allValidMonuments: ${allValidMonuments.size}"
    )

    MonumentJdbc.batchInsert(allValidMonuments)
  }

}
