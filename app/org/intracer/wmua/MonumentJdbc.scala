package org.intracer.wmua

import scalikejdbc._
import client.wlx.Monument

object MonumentJdbc extends SQLSyntaxSupport[Monument]{

  override val tableName = "monument"

  val c = MonumentJdbc.syntax("c")

  def apply(c: SyntaxProvider[Monument])(rs: WrappedResultSet): Monument = apply(c.resultName)(rs)

  def apply(c: ResultName[Monument])(rs: WrappedResultSet): Monument = new Monument(textParam = "",
    id = rs.string(c.id),
    name = rs.string(c.name),
    description = rs.stringOpt(c.description),
    article = None,
    place = rs.string(c.place),
    photo = rs.stringOpt(c.photo),
    gallery = rs.stringOpt(c.gallery),
    page =  rs.string(c.page)
  )

  def batchInsert(monuments: Seq[Monument]) {
    val column = MonumentJdbc.column
    DB localTx { implicit session =>
      val batchParams: Seq[Seq[Any]] = monuments.map(i => Seq(
        i.id,
        i.name,
        i.description,
//        i.article,
        i.place,
        i.photo,
        i.gallery,
        i.page
      ))
      withSQL {
        insert.into(MonumentJdbc).namedValues(
          column.id -> sqls.?,
          column.name -> sqls.?,
          column.description -> sqls.?,
          column.place -> sqls.?,
          column.photo -> sqls.?,
          column.gallery -> sqls.?,
          column.page -> sqls.?
        )
      }.batch(batchParams: _*).apply()
    }
  }

  def findAll()(implicit session: DBSession = autoSession): List[Monument] = withSQL {
    select.from(MonumentJdbc as c)
      //      .where.append(isNotDeleted)
      .orderBy(c.id)
  }.map(MonumentJdbc(c)).list.apply()

  def find(id: String)(implicit session: DBSession = autoSession): Option[Monument] = withSQL {
    select.from(MonumentJdbc as c).where.eq(c.id, id) //.and.append(isNotDeleted)
  }.map(MonumentJdbc(c)).single.apply()

}
