package db.scalikejdbc

import db.scalikejdbc.ImageJdbc.{cl, i}
import db.scalikejdbc.UserJdbc.{autoSession, createAlias}
import org.intracer.wmua.{User, UserRole}
import org.joda.time.DateTime
import play.api.data.validation.{Constraints, Invalid, Valid}
import play.api.libs.Codecs
import scalikejdbc._
import skinny.orm.SkinnyCRUDMapper

object UserJdbc extends SkinnyCRUDMapper[User] {

  implicit def session: DBSession = autoSession

  override val tableName = "users"

  val u = UserJdbc.syntax("u")
  val ur = UserRoleJdbc.ur
  override lazy val defaultAlias = createAlias("u")

  def isNotDeleted = sqls.isNull(u.deletedAt)

  def randomString(len: Int): String = {
    val rand = new scala.util.Random(System.nanoTime)
    val sb = new StringBuilder(len)
    val ab = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    for (i <- 0 until len) {
      sb.append(ab(rand.nextInt(ab.length)))
    }
    sb.toString()
  }

  def login(username: String, password: String): Option[User] = {
    byUserName(username).filter(user => {
      val passwordTrimmed = password.trim
      val inputHash = hash(user, passwordTrimmed)
      val dbHash = user.password.get
      inputHash == dbHash
    })
  }

  def hash(user: User, password: String): String =
    sha1(password)

  def sha1(input: String): String =
    Codecs.sha1(input.getBytes)

  def byUserName(username: String): Option[User] = {
    val unameTrimmed = username.trim.toLowerCase
    val users = Constraints.emailAddress(unameTrimmed) match {
      case Valid => findByEmail(username)
      case Invalid(errors) => findByAccount(unameTrimmed)
    }
    users.headOption
  }

  override def extract(rs: WrappedResultSet, c: ResultName[User]): User = User(
    id = Some(rs.int(c.id)),
    fullname = rs.string(c.fullname),
    email = rs.string(c.email),
//    roles = rs.string(c.roles).split(",").map(_.trim).toSet ++ Set("USER_ID_" + rs.int(c.id)),
//    contest = rs.longOpt(c.contest),
    password = Some(rs.string(c.password)),
    lang = rs.stringOpt(c.lang),
    wikiAccount = rs.stringOpt(c.wikiAccount),
    createdAt = rs.timestampOpt(c.createdAt).map(_.toJodaDateTime),
    deletedAt = rs.timestampOpt(c.deletedAt).map(_.toJodaDateTime)
  )

  def apply(c: ResultName[User])(rs: WrappedResultSet): User = new User(
    id = Some(rs.int(c.id)),
    fullname = rs.string(c.fullname),
    email = rs.string(c.email),
//    roles = rs.string(c.roles).split(",").map(_.trim).toSet ++ Set("USER_ID_" + rs.int(c.id)),
//    contest = rs.longOpt(c.contest),
    password = Some(rs.string(c.password)),
    lang = rs.stringOpt(c.lang),
    wikiAccount = rs.stringOpt(c.wikiAccount),
    createdAt = rs.timestampOpt(c.createdAt).map(_.toJodaDateTime),
    deletedAt = rs.timestampOpt(c.deletedAt).map(_.toJodaDateTime)
  )

//  def findByContest(contest: Long): Seq[User] =
//    where('contest -> contest).orderBy(u.id).apply()

  def findByContest(contestId: Long): Seq[User] =
    withSQL {
      select.from[User](UserJdbc as u)
        .innerJoin(UserRoleJdbc as ur)
        .on(u.id, ur.userId)
        .where.eq(ur.contestId, contestId)
    }.map(rs => (UserJdbc(u)(rs), UserRoleJdbc(ur)(rs))).list().apply().map {
      case (u, ur) =>
        u.copy(contest = Some(contestId), roles = Set(ur.role))
    }

  def findByRoundSelection(roundId: Long): Seq[User] = withSQL {
    import SelectionJdbc.s

    select.from(UserJdbc as u)
      .join(SelectionJdbc as s)
      .on(u.id, s.juryId)
      .where.eq(s.round, roundId)
      .groupBy(u.id)
      .orderBy(u.id)
  }.map(UserJdbc(u)).list().apply()

  def countByEmail(id: Long, email: String): Long =
    countBy(sqls
      .eq(column.email, email).and
      .ne(column.id, id))

  def findByEmail(email: String): Seq[User] =
    where('email -> email)
      .orderBy(u.id).apply()

  def findByAccount(account: String): Seq[User] =
    where('wikiAccount -> account)
      .orderBy(u.id).apply()

  def create(fullname: String,
             email: String,
             password: String,
             roles: Set[String],
             contest: Option[Long] = None,
             lang: Option[String] = None,
             createdAt: Option[DateTime] = Some(DateTime.now)
            ): User = {
    val id = withSQL {
      insert.into(UserJdbc).namedValues(
        column.fullname -> fullname,
        column.email -> email.trim.toLowerCase,
        column.password -> password,
//        column.roles -> roles.headOption.getOrElse("jury"),
//        column.contest -> contest,
        column.lang -> lang,
        column.createdAt -> createdAt)
    }.updateAndReturnGeneratedKey().apply()

    User(id = Some(id), fullname = fullname, email = email, password = Some(password),
      roles = roles ++ Set("USER_ID_" + id), contest = contest, createdAt = createdAt)
  }

  def create(user: User): User = {
    val id = withSQL {
      insert.into(UserJdbc).namedValues(
        column.fullname -> user.fullname,
        column.email -> user.email.trim.toLowerCase,
        column.wikiAccount -> user.wikiAccount,
        column.password -> user.password,
//        column.roles -> user.roles.headOption.getOrElse(""),
//        column.contest -> user.contest,
        column.lang -> user.lang,
        column.createdAt -> user.createdAt)
    }.updateAndReturnGeneratedKey().apply()

    user.copy(id = Some(id), roles = user.roles ++ Set("USER_ID_" + id))
  }

  def updateUser(id: Long, fullname: String, wikiAccount: Option[String],
                 email: String, roles: Set[String], lang: Option[String]): Unit =
    updateById(id)
      .withAttributes(
        'fullname -> fullname,
        'email -> email,
        'wikiAccount -> wikiAccount,
        'roles -> roles.head,
        'lang -> lang
      )

  def updateHash(id: Long, hash: String): Unit =
    updateById(id)
      .withAttributes('password -> hash)
}

object UserRoleJdbc extends SkinnyCRUDMapper[UserRole] {
  implicit def session: DBSession = autoSession
  val ur = UserRoleJdbc.syntax("ur")
  override lazy val defaultAlias = createAlias("ur")
  override val tableName = "user_roles"

  def addRole(userId: Long, contestId: Option[Long], role: String) = {
    create(UserRole(userId, contestId, role))
  }

  def create(userRole: UserRole) = {
    val id = withSQL {
      insert.into(UserRoleJdbc).namedValues(
        column.userId -> userRole.userId,
        column.contestId -> userRole.contestId,
        column.role -> userRole.role
      )
    }.update().apply()
  }

  override def extract(rs: WrappedResultSet, c: ResultName[UserRole]): UserRole = UserRole(
    userId = rs.int(c.userId),
    contestId = rs.longOpt(c.contestId),
    role = rs.string(c.role)
  )

}