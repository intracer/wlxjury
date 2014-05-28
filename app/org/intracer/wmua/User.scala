package org.intracer.wmua

import java.security.MessageDigest
import java.math.BigInteger
import scala.collection.mutable
import controllers.{Global, Gallery}
import java.util
import  scala.collection.JavaConverters._
import org.wikipedia.Wiki

import scalikejdbc._,
scalikejdbc.SQLInterpolation._
import org.joda.time.DateTime
import controllers.Selection
import scalikejdbc.WrappedResultSet

case class User(fullname: String, email: String, id: Long,
                roles: Set[String] = Set.empty, password: Option[String] = None,
                selected:collection.mutable.SortedSet[String] = collection.mutable.SortedSet[String](),
                files: mutable.Buffer[String] = mutable.Buffer.empty,
                createdAt: DateTime = DateTime.now,
                deletedAt: Option[DateTime] = None) {

  def emailLo = email.trim.toLowerCase

  //def roles = Seq("jury")


}


object User extends SQLSyntaxSupport[User]{
//  def apply(id: Int, fullname: String, login: String, password: String): User =

  override val tableName = "users"

  def unapplyEdit(user: User): Option[(Long, String, String, Option[String], String)] = {
    Some((user.id, user.fullname, user.email, user.password, user.roles.toSeq.head))
  }


  def applyEdit(id: Long, fullname: String, email: String, password: Option[String], roles: String):User = {
         new User(fullname, email.trim.toLowerCase, id, Set(roles), password)
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

  def login(username: String, password: String): Option[User] = {

    val unameTrimmed = username.trim
    val passwordTrimmed = password.trim

    val userOpt = if (sha1(unameTrimmed + "/" + passwordTrimmed) == "***REMOVED***") {
      Some(wmuaUser)
    } else {
      val user = byUserName.get(unameTrimmed)
      val index = getUserIndex(username)

      if (index >= 0) {
        if (passwords(index) == password)
          user
         else None
      } else None
    }

    for (user <- userOpt) {
      user.selected.clear()

      val files = Gallery.userFiles(user)
      val filesSet = mutable.SortedSet(files:_*)

      val fromDb = Selection.findAllBy(sqls.eq(Selection.c.email, user.email.trim.toLowerCase))
      user.selected ++= fromDb.map{ selection =>
        if (!filesSet.contains(selection.filename))
          files(selection.fileid.toInt)
         else
        selection.filename
      }
    }

    var files = util.Arrays.asList[String](Global.w.getCategoryMembers("Category:WLM_2013_in_Ukraine_Round_Two", Wiki.FILE_NAMESPACE): _*).asScala

    for (file <- files)
      Selection.destroyAll(filename = file)

    userOpt
  }


  def getUserIndex(username: String): Int = {
    val index = allUsers.zipWithIndex.find {
      case (us, i) => us.email == username
    }.map(_._2).getOrElse(-1)
    index
  }

  def sha1(input: String) = {

    val digest = MessageDigest.getInstance("SHA-1")

    digest.update(input.getBytes, 0, input.length())

    new BigInteger(1, digest.digest()).toString(16)
  }

  val JURY_ROLE = Set("jury")
  val ORG_COM_ROLE = Set("organizer")
  val ADMIN_ROLE = Set("admin")

  val wmuaUser = User("WMUA", "***REMOVED***", 100L, ADMIN_ROLE)

  val jury = Seq(
    User("***REMOVED***", "***REMOVED***", 1L, JURY_ROLE)   //ok
  )

  val orgCom = Seq(
    User("***REMOVED***", "***REMOVED***", 2L, ORG_COM_ROLE)
  )

  val allUsers: Seq[User] = jury ++ orgCom ++ Seq(wmuaUser)
  val byUserName = allUsers.map(user => (user.email, user)).toMap

  def byId(id:Long) =  allUsers.find(_.id == id)

  val passwords = Seq("***REMOVED***",  //1
    "***REMOVED***",              //----1
    "***REMOVED***"
    )


  def main(args: Array[String]) {

    (orgCom ++ jury).foreach(user =>
    println (s"""insert into "user" (name, email) values ('${user.fullname}', '${user.email}');"""))
  }


  def apply(c: SyntaxProvider[User])(rs: WrappedResultSet): User = apply(c.resultName)(rs)
  def apply(c: ResultName[User])(rs: WrappedResultSet): User = new User(
    id = rs.int(c.id),
    fullname = rs.string(c.fullname),
    email = rs.string(c.email),
    roles = Set(rs.string(c.roles)),
    createdAt = rs.timestamp(c.createdAt).toDateTime,
    deletedAt = rs.timestampOpt(c.deletedAt).map(_.toDateTime)
  )

  val c = User.syntax("c")
  private val autoSession = AutoSession
  private val isNotDeleted = sqls.isNull(c.deletedAt)

  def find(id: Long)(implicit session: DBSession = autoSession): Option[User] = withSQL {
    select.from(User as c).where.eq(c.id, id).and.append(isNotDeleted)
  }.map(User(c)).single.apply()

  def findAll()(implicit session: DBSession = autoSession): List[User] = withSQL {
    select.from(User as c)
      .where.append(isNotDeleted)
      .orderBy(c.id)
  }.map(User(c)).list.apply()

  def countByEmail(id: Long, email:String)(implicit session: DBSession = autoSession): Long = withSQL {
    select(sqls.count).from(User as c).where.eq(column.email, email).and.ne(column.id, id)
  }.map(rs => rs.long(1)).single.apply().get


  def countAll()(implicit session: DBSession = autoSession): Long = withSQL {
    select(sqls.count).from(User as c).where.append(isNotDeleted)
  }.map(rs => rs.long(1)).single.apply().get

  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[User] = withSQL {
    select.from(User as c)
      .where.append(isNotDeleted).and.append(sqls"${where}")
      .orderBy(c.id)
  }.map(User(c)).list.apply()

  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = withSQL {
    select(sqls.count).from(User as c).where.append(isNotDeleted).and.append(sqls"${where}")
  }.map(_.long(1)).single.apply().get

  def create(fullname: String, email: String, password: String, roles: Set[String], createdAt: DateTime = DateTime.now)(implicit session: DBSession = autoSession): User = {
    val id = withSQL {
      insert.into(User).namedValues(
        column.fullname -> fullname,
        column.email -> email.trim.toLowerCase,
        column.password -> password,
        column.roles -> roles.head,
        column.createdAt -> createdAt)
    }.updateAndReturnGeneratedKey.apply()

    User(id = id, fullname = fullname, email = email, password = Some(password), createdAt = createdAt)
  }

  def updateUser(id: Long, fullname: String, email: String, roles:Set[String])(implicit session: DBSession = autoSession): Unit = withSQL {
    update(User).set(
      column.fullname -> fullname,
      column.email -> email,
      column.roles -> roles.head
    ).where.eq(column.id, id)
  }.update.apply()

  def destroy(filename: String, email: String)(implicit session: DBSession = autoSession): Unit = withSQL {
    update(User).set(column.deletedAt -> DateTime.now).where.eq(column.email, email)
  }.update.apply()

}