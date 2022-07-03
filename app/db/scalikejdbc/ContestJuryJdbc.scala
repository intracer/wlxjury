package db.scalikejdbc

import _root_.play.api.i18n.Messages
import controllers.Greeting
import db.scalikejdbc.ContestUser.{createAlias, updateBy, where}
import scalikejdbc._
import skinny.orm.{SkinnyCRUDMapper, SkinnyJoinTable}

object ContestJuryJdbc extends SkinnyCRUDMapper[ContestJury] {

  var messages: Messages = _

  override val tableName = "contest_jury"

  implicit def session: DBSession = autoSession

  override lazy val defaultAlias = createAlias("m")

  override def extract(rs: WrappedResultSet, c: ResultName[ContestJury]): ContestJury = db.scalikejdbc.ContestJury(
    id = rs.longOpt(c.id),
    name = rs.string(c.name),
    year = rs.int(c.year),
    country = rs.string(c.country),
    images = rs.stringOpt(c.images),
    categoryId = rs.longOpt(c.categoryId),
    currentRound = rs.longOpt(c.currentRound),
    monumentIdTemplate = rs.stringOpt(c.monumentIdTemplate),
    greeting = Greeting(
      rs.stringOpt(c.column("greeting")),
      rs.booleanOpt(c.column("use_greeting")).getOrElse(true)
    ),
    campaign = rs.stringOpt(c.campaign),
  )

  def setImagesSource(id: Long, images: Option[String]): Int = {
    val categoryId = images.map(CategoryJdbc.findOrInsert)

    updateById(id)
      .withAttributes('images -> images, 'categoryId -> categoryId)
  }

  def updateGreeting(id: Long, greeting: Greeting): Int =
    updateById(id)
      .withAttributes(
        'greeting -> greeting.text,
        'use_greeting -> greeting.use
      )

  def setCurrentRound(id: Long, round: Option[Long]): Int =
    updateById(id)
      .withAttributes('currentRound -> round)

  def create(id: Option[Long],
                      name: String,
                      year: Int,
                      country: String,
                      images: Option[String] = None,
                      categoryId: Option[Long] = None,
                      currentRound: Option[Long] = None,
                      monumentIdTemplate: Option[String] = None,
                      campaign: Option[String] = None,
            ): ContestJury = {
    val dbId = withSQL {
      insert.into(ContestJuryJdbc).namedValues(
        column.id -> id,
        column.name -> name,
        column.year -> year,
        column.country -> country,
        column.images -> images,
        column.categoryId -> categoryId,
        column.currentRound -> currentRound,
        column.monumentIdTemplate -> monumentIdTemplate,
        column.campaign -> campaign
      )
    }.updateAndReturnGeneratedKey().apply()

    db.scalikejdbc.ContestJury(id = Some(dbId),
      name = name,
      year = year,
      country = country,
      images = images,
      categoryId = categoryId,
      currentRound = currentRound,
      monumentIdTemplate = monumentIdTemplate,
      greeting = Greeting(None, true),
      campaign = campaign)
  }

  def batchInsert(contests: Seq[ContestJury]): Seq[Int] = {
    val column = ContestJuryJdbc.column
    DB localTx { implicit session =>
      val batchParams: Seq[Seq[Any]] = contests.map(c => Seq(
        c.id,
        c.name,
        c.year,
        c.country,
        c.images,
        c.currentRound,
        c.monumentIdTemplate,
        c.campaign
      ))
      withSQL {
        insert.into(ContestJuryJdbc).namedValues(
          column.id -> sqls.?,
          column.name -> sqls.?,
          column.year -> sqls.?,
          column.country -> sqls.?,
          column.images -> sqls.?,
          column.currentRound -> sqls.?,
          column.monumentIdTemplate -> sqls.?,
          column.campaign -> sqls.?,
        )
      }.batch(batchParams: _*).apply()
    }
  }
}

case class ContestUser(contestId: Long, userId: Long, role: String)

object ContestUser extends SkinnyJoinTable[ContestUser] {
  override def defaultAlias = createAlias("contest_user")
  lazy val cu = ContestUser.syntax("contest_user")

  override def extract(rs: WrappedResultSet, cu: ResultName[ContestUser]): ContestUser =
    ContestUser(rs.long(cu.contestId), rs.long(cu.userId), rs.string(cu.role))

  def apply(c: ContestJury, u: User): Option[ContestUser] =
    for (cId <- c.id; uId <- u.id) yield ContestUser(cId, uId, u.roles.head)

  def availableJurors(contestId: Long): List[ContestUser] =
    where('contestId -> contestId, 'role -> User.JURY_ROLE).apply()

  def byContestId(contestId: Long): List[ContestUser] =
    where('contestId -> contestId).apply()

  def setRole(contestId: Long, userId: Long, role: String): Int =
    updateBy(sqls.eq(cu.contestId, contestId).and.eq(cu.userId, userId))
      .withAttributes('role -> role)
}
