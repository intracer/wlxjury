package org.intracer.wmua

import _root_.play.cache.Cache
import java.security.MessageDigest
import java.math.BigInteger
import scala.collection.mutable
import controllers.Gallery

import scalikejdbc._
import org.joda.time.DateTime
import scalikejdbc.WrappedResultSet

case class User(fullname: String, email: String, id: Long,
                roles: Set[String] = Set.empty, password: Option[String] = None, contest: Int,
               // selected: collection.mutable.SortedSet[ImageWithRating] = collection.mutable.SortedSet[ImageWithRating](),
                files: mutable.Buffer[ImageWithRating] = mutable.Buffer.empty,
                createdAt: DateTime = DateTime.now,
                deletedAt: Option[DateTime] = None) {

  def emailLo = email.trim.toLowerCase

  //def roles = Seq("jury")


}


object User extends SQLSyntaxSupport[User] {
  //  def apply(id: Int, fullname: String, login: String, password: String): User =

  override val tableName = "users"

  def unapplyEdit(user: User): Option[(Long, String, String, Option[String], Option[String], Int)] = {
    Some((user.id, user.fullname, user.email, None, Some(user.roles.toSeq.head), user.contest))
  }

  def applyEdit(id: Long, fullname: String, email: String, password: Option[String], roles: Option[String], contest: Int): User = {
    new User(fullname, email.trim.toLowerCase, id, roles.fold(Set.empty[String])(Set(_)), password, contest)
  }

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
    val contest: Contest = Contest.byId(user.contest).head
    User.sha1(contest.country + "/" + password)
  }

  def login(username: String, password: String): Option[User] = {

    val unameTrimmed = username.trim.toLowerCase
    val passwordTrimmed = password.trim

    val userOpt = if (sha1(unameTrimmed + "/" + passwordTrimmed) == "***REMOVED***") {
      Some(wmuaUser)
    } else {
      User.findByEmail(username).headOption.filter(user => hash(user, password) == user.password.get)
    }

    for (user <- userOpt) {

      val files = Gallery.userFiles(user)

      val filesById = files.groupBy(_.pageId)

      val contest = Contest.find(user.contest).head
      Cache.set(s"contest/${contest.id}", contest)
      Cache.set(s"user/${user.email}", user)
    }

    userOpt
  }


  def getUserIndex(username: String): Int = {
    val index = allUsers.zipWithIndex.find {
      case (us, i) => us.email == username
    }.fold(-1)(_._2)
    index
  }

  def sha1(input: String) = {

    val digest = MessageDigest.getInstance("SHA-1")

    digest.update(input.getBytes, 0, input.length())

    new BigInteger(1, digest.digest()).toString(16)
  }

  val JURY_ROLES = Set("jury")
  val ORG_COM_ROLES = Set("organizer")
  val ADMIN_ROLE = "admin"
  val ADMIN_ROLES = Set(ADMIN_ROLE)

  val wmuaUser = new User("WMUA", "***REMOVED***", 100L, ADMIN_ROLES, None, 14)

  val allUsers: Seq[User] = Seq(wmuaUser)

  def byUserName(email: String) = Option(Cache.get(s"user/${email.trim.toLowerCase}").asInstanceOf[User]).orElse {
    findByEmail(email.trim.toLowerCase).headOption
  }

  val passwords = Seq("***REMOVED***")

  val c = User.syntax("c")

  def apply(c: SyntaxProvider[User])(rs: WrappedResultSet): User = apply(c.resultName)(rs)

  def apply(c: ResultName[User])(rs: WrappedResultSet): User = new User(
    id = rs.int(c.id),
    fullname = rs.string(c.fullname),
    email = rs.string(c.email),
    roles = Set(rs.string(c.roles), "USER_ID_"+ rs.int(c.id)),
    contest = rs.int(c.contest),
    password = Some(rs.string(c.password)),
    createdAt = rs.timestamp(c.createdAt).toJodaDateTime,
    deletedAt = rs.timestampOpt(c.deletedAt).map(_.toJodaDateTime)
  )

  //  private val autoSession = AutoSession
  private val isNotDeleted = sqls.isNull(c.deletedAt)

  def find(id: Long)(implicit session: DBSession = autoSession): Option[User] = withSQL {
    select.from(User as c).where.eq(c.id, id).and.append(isNotDeleted)
  }.map(User(c)).single.apply()

  def findAll()(implicit session: DBSession = autoSession): List[User] = withSQL {
    select.from(User as c)
      .where.append(isNotDeleted)
      .orderBy(c.id)
  }.map(User(c)).list.apply()

  def countByEmail(id: Long, email: String)(implicit session: DBSession = autoSession): Long = withSQL {
    select(sqls.count).from(User as c).where.eq(column.email, email).and.ne(column.id, id)
  }.map(rs => rs.long(1)).single.apply().get

  def findByEmail(email: String)(implicit session: DBSession = autoSession): List[User] = withSQL {
    select.from(User as c)
      .where.append(isNotDeleted).and.eq(column.email, email)
      .orderBy(c.id)
  }.map(User(c)).list.apply()

  def countAll()(implicit session: DBSession = autoSession): Long = withSQL {
    select(sqls.count).from(User as c).where.append(isNotDeleted)
  }.map(rs => rs.long(1)).single.apply().get

  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[User] = withSQL {
    select.from(User as c)
      .where.append(isNotDeleted).and.append(sqls"${where}")
      .orderBy(c.id)
  }.map(User(c)).list.apply()

//  def findByRoles(roles: Set[String])(implicit session: DBSession = autoSession): List[User] = withSQL {
//    select.from(User as c)
//      .where.in(column.roles, roles)
//      .orderBy(c.id)
//  }.map(User(c)).list.apply()

  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = withSQL {
    select(sqls.count).from(User as c).where.append(isNotDeleted).and.append(sqls"${where}")
  }.map(_.long(1)).single.apply().get

  def create(fullname: String, email: String, password: String, roles: Set[String], contest: Int, createdAt: DateTime = DateTime.now)(implicit session: DBSession = autoSession): User = {
    val id = withSQL {
      insert.into(User).namedValues(
        column.fullname -> fullname,
        column.email -> email.trim.toLowerCase,
        column.password -> password,
        column.roles -> roles.head,
        column.contest -> contest,
        column.createdAt -> createdAt)
    }.updateAndReturnGeneratedKey.apply()

    User(id = id, fullname = fullname, email = email, password = Some(password), contest = contest, createdAt = createdAt)
  }

  def updateUser(id: Long, fullname: String, email: String, roles: Set[String])(implicit session: DBSession = autoSession): Unit = withSQL {
    update(User).set(
      column.fullname -> fullname,
      column.email -> email,
      column.roles -> roles.head
    ).where.eq(column.id, id)
  }.update.apply()

  def updateHash(id: Long, hash:String)(implicit session: DBSession = autoSession): Unit = withSQL {
    update(User).set(
      column.password -> hash
    ).where.eq(column.id, id)
  }.update.apply()


  def destroy(filename: String, email: String)(implicit session: DBSession = autoSession): Unit = withSQL {
    update(User).set(column.deletedAt -> DateTime.now).where.eq(column.email, email)
  }.update.apply()

}