package db.scalikejdbc

import _root_.play.api.i18n.Messages
import controllers.Greeting
import org.intracer.wmua.HasId
import scalikejdbc._
import skinny.orm.{SkinnyCRUDMapper, SkinnyJoinTable}

case class ContestJury(id: Option[Long],
                       name: String,
                       year: Int,
                       country: String,
                       images: Option[String],
                       categoryId: Option[Long] = None,
                       currentRound: Option[Long] = None,
                       monumentIdTemplate: Option[String] = None,
                       greeting: Greeting = Greeting(None, use = true),
                       campaign: Option[String] = None,
                       users: Seq[User] = Nil) extends HasId {
  //def localName = Messages("wiki.loves.earth." + country, year)(messages)
  def fullName = s"$name $year in $country"

  def getImages = images.getOrElse("Category:Images from " + name)

  def addUser(user: User, role: String)(implicit session: DBSession): Unit = {
    for (userId <- user.id; contestId <- id) yield {
      addUser(new ContestUser(contestId, userId, role))
    }
  }

  def addUser(cu: ContestUser)(implicit session: DBSession): Unit = {
    ContestUser.withColumns { c =>
      ContestUser.createWithNamedValues(c.contestId -> id, c.userId -> cu.userId, c.role -> cu.role)
    }
  }

  def addUsers(users: Seq[ContestUser]): Unit = {
    DB localTx { implicit session =>
      withSQL {
        val c = ContestUser.column
        insert.into(ContestUser)
          .namedValues(c.contestId -> sqls.?, c.userId -> sqls.?, c.role -> sqls.?)
      }.batch(users.map(cu => Seq(id, cu.userId, cu.role)): _*).apply()
    }
  }

  def deleteUser(user: User)(implicit session: DBSession = RoundUser.autoSession): Unit = {
    ContestUser.withColumns { c =>
      withSQL {
        delete.from(ContestUser).where.eq(c.contestId, id).and.eq(c.userId, user.id)
      }.update.apply()
    }
  }

}

object ContestJury extends SkinnyCRUDMapper[ContestJury] {

  var messages: Messages = _

  override val tableName = "contest_jury"

  implicit def session: DBSession = autoSession

  override lazy val defaultAlias = createAlias("c")

  //  lazy val contestUser =
  hasManyThrough[User](
    through = ContestUser,
    many = User,
    merge = (contest, users) => contest.copy(users = users)).byDefault

  override def extract(rs: WrappedResultSet, c: ResultName[ContestJury]): ContestJury = ContestJury(
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
      insert.into(ContestJury).namedValues(
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

    ContestJury(id = Some(dbId),
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
    val column = ContestJury.column
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
        insert.into(ContestJury).namedValues(
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
