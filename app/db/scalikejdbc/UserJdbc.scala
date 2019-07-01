package db.scalikejdbc

import java.time.ZonedDateTime
import java.util.UUID

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.IdentityService
import com.mohiva.play.silhouette.impl.providers.CommonSocialProfile
import org.intracer.wmua.User
import play.api.data.validation.{Constraints, Invalid, Valid}
import play.api.libs.Codecs
import scalikejdbc._
import skinny.orm.SkinnyCRUDMapper

import scala.concurrent.Future

object UserJdbc extends SkinnyCRUDMapper[User] with UserService {

  implicit def session: DBSession = autoSession

  override val tableName = "users"

  val u = UserJdbc.syntax("u")

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
    val users = Constraints.emailAddress()(unameTrimmed) match {
      case Valid => findByEmail(username)
      case Invalid(errors) => findByAccount(unameTrimmed)
    }
    users.headOption
  }

  override lazy val defaultAlias = createAlias("u")

  override def extract(rs: WrappedResultSet, c: ResultName[User]): User = User(
    id = Some(rs.int(c.id)),
    fullname = rs.string(c.fullname),
    email = rs.string(c.email),
    roles = rs.string(c.roles).split(",").map(_.trim).toSet ++ Set("USER_ID_" + rs.int(c.id)),
    contestId = rs.longOpt(c.contestId),
    password = Some(rs.string(c.password)),
    lang = rs.stringOpt(c.lang),
    wikiAccount = rs.stringOpt(c.wikiAccount),
    createdAt = rs.timestampOpt(c.createdAt).map(_.toZonedDateTime),
    deletedAt = rs.timestampOpt(c.deletedAt).map(_.toZonedDateTime)
  )

  def apply(c: ResultName[User])(rs: WrappedResultSet): User = new User(
    id = Some(rs.int(c.id)),
    fullname = rs.string(c.fullname),
    email = rs.string(c.email),
    roles = rs.string(c.roles).split(",").map(_.trim).toSet ++ Set("USER_ID_" + rs.int(c.id)),
    contestId = rs.longOpt(c.contestId),
    password = Some(rs.string(c.password)),
    lang = rs.stringOpt(c.lang),
    wikiAccount = rs.stringOpt(c.wikiAccount),
    createdAt = rs.timestampOpt(c.createdAt).map(_.toZonedDateTime),
    deletedAt = rs.timestampOpt(c.deletedAt).map(_.toZonedDateTime)
  )

  def findByContest(contest: Long): Seq[User] =
    where('contestId -> contest).orderBy(u.id).apply()

  def findByRoundSelection(roundId: Long): Seq[User] = withSQL {
    import SelectionJdbc.s

    select.from(UserJdbc as u)
      .join(SelectionJdbc as s)
      .on(u.id, s.juryId)
      .where.eq(s.roundId, roundId)
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
             contestId: Option[Long] = None,
             lang: Option[String] = None,
             createdAt: Option[ZonedDateTime] = Some(ZonedDateTime.now)
            ): User = {
    val id = withSQL {
      insert.into(UserJdbc).namedValues(
        column.fullname -> fullname,
        column.email -> email.trim.toLowerCase,
        column.password -> password,
        column.roles -> roles.headOption.getOrElse("jury"),
        column.contestId -> contestId,
        column.lang -> lang,
        column.createdAt -> createdAt)
    }.updateAndReturnGeneratedKey().apply()

    User(id = Some(id), fullname = fullname, email = email, password = Some(password),
      roles = roles ++ Set("USER_ID_" + id), contestId = contestId, createdAt = createdAt)
  }

  def create(user: User): User = {
    val id = withSQL {
      insert.into(UserJdbc).namedValues(
        column.fullname -> user.fullname,
        column.email -> user.email.trim.toLowerCase,
        column.wikiAccount -> user.wikiAccount,
        column.password -> user.password,
        column.roles -> user.roles.headOption.getOrElse(""),
        column.contestId -> user.contestId,
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

  override def retrieve(loginInfo: LoginInfo): Future[Option[User]] = ???

  /**
    * Retrieves a user that matches the specified ID.
    *
    * @param id The ID to retrieve a user.
    * @return The retrieved user or None if no user could be retrieved for the given ID.
    */
  override def retrieve(id: UUID): Future[Option[User]] = ???

  /**
    * Saves a user.
    *
    * @param user The user to save.
    * @return The saved user.
    */
  override def save(user: User): Future[User] = ???

  /**
    * Saves the social profile for a user.
    *
    * If a user exists for this profile then update the user, otherwise create a new user with the given profile.
    *
    * @param profile The social profile to save.
    * @return The user for whom the profile was saved.
    */
  override def save(profile: CommonSocialProfile): Future[User] = ???
}