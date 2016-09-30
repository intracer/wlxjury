package db.scalikejdbc

import java.math.BigInteger
import java.security.MessageDigest

import db.UserDao
import org.intracer.wmua.{ContestJury, User}
import org.joda.time.DateTime
import scalikejdbc._

object UserJdbc extends SQLSyntaxSupport[User] with UserDao {
  //  def apply(id: Int, fullname: String, login: String, password: String): User =
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

  def hash(user: User, password: String): String = {
    sha1(password)
  }

  def login(username: String, password: String): Option[User] = {

    val unameTrimmed = username.trim.toLowerCase
    val passwordTrimmed = password.trim

    val userOpt = findByEmail(username).headOption.filter(user => {
      val inputHash = hash(user, password)
      val dbHash = user.password.get
      inputHash == dbHash
    })

    //    for (user <- userOpt) {
    //      val contest = ContestJuryJdbc.find(user.contest).get
    //      Cache.set(s"contest/${contest.id}", contest)
    //      Cache.set(s"user/${user.email}", user)
    //    }

    userOpt
  }

  def sha1(input: String) = {

    val digest = MessageDigest.getInstance("SHA-1")

    digest.update(input.getBytes, 0, input.length())

    new BigInteger(1, digest.digest()).toString(16)
  }

  def byUserName(email: String) = {
    findByEmail(email.trim.toLowerCase).headOption
  }

  def apply(c: SyntaxProvider[User])(rs: WrappedResultSet): User = apply(c.resultName)(rs)

  def apply(c: ResultName[User])(rs: WrappedResultSet): User = new User(
    id = Some(rs.int(c.id)),
    fullname = rs.string(c.fullname),
    email = rs.string(c.email),
    roles = rs.string(c.roles).split(",").map(_.trim).toSet ++ Set("USER_ID_" + rs.int(c.id)),
    contest = rs.longOpt(c.contest),
    password = Some(rs.string(c.password)),
    lang = rs.stringOpt(c.lang),
    createdAt = rs.timestampOpt(c.createdAt).map(_.toJodaDateTime),
    deletedAt = rs.timestampOpt(c.deletedAt).map(_.toJodaDateTime)
  )

  override def find(id: Long): Option[User] = withSQL {
    select.from(UserJdbc as u).where.eq(u.id, id).and.append(isNotDeleted)
  }.map(UserJdbc(u)).single().apply()

  override def findAll(): Seq[User] = withSQL {
    select.from(UserJdbc as u)
      .where.append(isNotDeleted)
      .orderBy(u.id)
  }.map(UserJdbc(u)).list().apply()

  override def findByContest(contest: Long): Seq[User] = withSQL {
    select.from(UserJdbc as u)
      .where.append(isNotDeleted).and.
      eq(column.contest, contest)
      .orderBy(u.id)
  }.map(UserJdbc(u)).list().apply()

  def findByRoundSelection(roundId: Long): Seq[User] = withSQL {
    import SelectionJdbc.s

    select.from(UserJdbc as u)
      .join(SelectionJdbc as s)
      .on(u.id, s.juryId)
      .where.eq(s.round, roundId)
      .groupBy(u.id)
      .orderBy(u.id)
  }.map(UserJdbc(u)).list().apply()


  override def countByEmail(id: Long, email: String): Long = withSQL {
    select(sqls.count).from(UserJdbc as u).where.eq(column.email, email).and.ne(column.id, id)
  }.map(rs => rs.long(1)).single().apply().get

  override def findByEmail(email: String): Seq[User] = {
    val users = withSQL {
      select.from(UserJdbc as u)
        .where.append(isNotDeleted).and.eq(column.email, email)
        .orderBy(u.id)
    }.map(UserJdbc(u)).list().apply()
    users
  }

  override def countAll(): Long = withSQL {
    select(sqls.count).from(UserJdbc as u).where.append(isNotDeleted)
  }.map(rs => rs.long(1)).single().apply().get

  def findAllBy(where: SQLSyntax): List[User] = withSQL {
    select.from(UserJdbc as u)
      .where.append(isNotDeleted).and.append(sqls"$where")
      .orderBy(u.id)
  }.map(UserJdbc(u)).list().apply()

  //  def findByRoles(roles: Set[String]): List[User] = withSQL {
  //    select.from(User as c)
  //      .where.in(column.roles, roles)
  //      .orderBy(c.id)
  //  }.map(User(c)).list.apply()

  def countBy(where: SQLSyntax): Long = withSQL {
    select(sqls.count).from(UserJdbc as u).where.append(isNotDeleted).and.append(sqls"$where")
  }.map(_.long(1)).single().apply().get

  override def create(
                       fullname: String,
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
        column.roles -> roles.headOption.getOrElse("jury"),
        column.contest -> contest,
        column.lang -> lang,
        column.createdAt -> createdAt)
    }.updateAndReturnGeneratedKey().apply()

    User(id = Some(id), fullname = fullname, email = email, password = Some(password), roles = roles ++ Set("USER_ID_" + id), contest = contest, createdAt = createdAt)
  }

  override def create(user: User): User = {
    val id = withSQL {
      insert.into(UserJdbc).namedValues(
        column.fullname -> user.fullname,
        column.email -> user.email.trim.toLowerCase,
        column.password -> user.password,
        column.roles -> user.roles.head,
        column.contest -> user.contest,
        column.lang -> user.lang,
        column.createdAt -> user.createdAt)
    }.updateAndReturnGeneratedKey().apply()

    user.copy(id = Some(id), roles = user.roles ++ Set("USER_ID_" + id))
  }

  override def updateUser(id: Long, fullname: String, email: String, roles: Set[String], lang: Option[String]): Unit = withSQL {
    update(UserJdbc).set(
      column.fullname -> fullname,
      column.email -> email,
      column.roles -> roles.head,
      column.lang -> lang
    ).where.eq(column.id, id)
  }.update().apply()

  override def updateHash(id: Long, hash: String): Unit = withSQL {
    update(UserJdbc).set(
      column.password -> hash
    ).where.eq(column.id, id)
  }.update().apply()


  override def destroy(filename: String, email: String): Unit = withSQL {
    update(UserJdbc).set(column.deletedAt -> DateTime.now).where.eq(column.email, email)
  }.update().apply()

}