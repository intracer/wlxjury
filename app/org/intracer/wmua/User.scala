package org.intracer.wmua

import java.security.MessageDigest
import java.math.BigInteger
import scala.collection.mutable
import play.Logger
import controllers.{Gallery, Selection}
import scalikejdbc.SQLInterpolation._
import scala.Some

case class User(fullname: String, login: String, id: String,
                selected:collection.mutable.SortedSet[String] = collection.mutable.SortedSet[String](),
                files: mutable.Buffer[String] = mutable.Buffer.empty) {

}


object User {
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

      val fromDb = Selection.findAllBy(sqls.eq(Selection.c.email, user.login.trim.toLowerCase))
      user.selected ++= fromDb.map{ selection =>
        if (!filesSet.contains(selection.filename))
          files(selection.fileid.toInt)
         else
        selection.filename
      }
    }

    userOpt
  }


  def getUserIndex(username: String): Int = {
    val index = allUsers.zipWithIndex.find {
      case (us, i) => us.login == username
    }.map(_._2).getOrElse(-1)
    index
  }

  def sha1(input: String) = {

    val digest = MessageDigest.getInstance("SHA-1")

    digest.update(input.getBytes, 0, input.length())

    new BigInteger(1, digest.digest()).toString(16)
  }

  val wmuaUser = User("WMUA", "WMUA", "1")

  val jury = Seq(
    User("***REMOVED***", "***REMOVED***", "1"),   //ok
    User("***REMOVED***" /*"***REMOVED***"*/, "***REMOVED***", "2"),
    User("***REMOVED***", "***REMOVED***", "3"),//ok
    User("***REMOVED***" /*"***REMOVED***"*/, "***REMOVED***", "4"),   //ok
    User("***REMOVED***", "***REMOVED***", "5"),
    User("***REMOVED***" /*"***REMOVED***"*/, "***REMOVED***", "6"),//ok
    User("***REMOVED***" , "***REMOVED***", "7"),//ok
    User("***REMOVED***" /*"***REMOVED***"*/, "***REMOVED***", "8"), //ok
    User("***REMOVED***" , "***REMOVED***", "9"),   //ok
    User("***REMOVED***", "***REMOVED***", "10"),//ok
    User("***REMOVED***" , "***REMOVED***", "11"),
    User("***REMOVED***", "***REMOVED***", "12"),
    User("***REMOVED***", "***REMOVED***", "13"),//ok
    User("***REMOVED***", "***REMOVED***", "14"),//ok
    User("***REMOVED***"/*"***REMOVED***"*/, "***REMOVED***", "15"),
    User("***REMOVED***" /*"***REMOVED***"*/, "***REMOVED***", "16")//ok
  )

  val orgCom = Seq(
    User("***REMOVED***", "***REMOVED***", "1"),
    User("***REMOVED***", "***REMOVED***", "2"),
    User("***REMOVED***", "***REMOVED***", "3"),
    User("***REMOVED***", "***REMOVED***", "4"),
    User("***REMOVED***", "***REMOVED***", "5"),
    User("***REMOVED***", "***REMOVED***", "6"),
    User("***REMOVED***", "***REMOVED***", "7"),
    User("***REMOVED***", "***REMOVED***", "8"),
    User("***REMOVED***", "***REMOVED***", "9"),
    User("***REMOVED***", "***REMOVED***", "10"),
    User("***REMOVED***", "***REMOVED***", "10")
  )

  val allUsers: Seq[User] = jury ++ orgCom ++ Seq(wmuaUser)
  val byUserName = allUsers.map(user => (user.login, user)).toMap

  val passwords = Seq("***REMOVED***",  //1
    "***REMOVED***",                       //2
    "***REMOVED***",                       //3
    "***REMOVED***",                          //4
    "***REMOVED***",                             //5
    "***REMOVED***", //6
    "***REMOVED***",    //7
    "***REMOVED***",       //8
    "***REMOVED***",          //9
    "***REMOVED***",             //10
    "***REMOVED***",             //11
    "***REMOVED***",             //12
    "***REMOVED***",             //13
    "***REMOVED***",             //14
    "***REMOVED***",             //15
    "***REMOVED***",             //16
    "***REMOVED***",              //----1
    "***REMOVED***",               //2
    "***REMOVED***",               //3
    "***REMOVED***",               //4
    "***REMOVED***",                //5
    "***REMOVED***",                // 6
    "***REMOVED***",                // 7
    "***REMOVED***",                // 8
    "***REMOVED***",                // 9
    "***REMOVED***",                // 10
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***")


  def main(args: Array[String]) {

    (orgCom ++ jury).foreach(user =>
    println (s"""insert into "user" (name, login) values ('${user.fullname}', '${user.login}');"""))
  }

}