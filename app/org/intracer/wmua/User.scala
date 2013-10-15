package org.intracer.wmua

import java.security.MessageDigest
import java.math.BigInteger
import scala.collection.mutable
import play.Logger
import controllers.{Gallery, Selection}
import scalikejdbc.SQLInterpolation._
import scala.Some

case class User(fullname: String, login: String, id: String,
                selected:collection.mutable.SortedSet[Int] = collection.mutable.SortedSet[Int](),
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
      val index = allUsers.zipWithIndex.find{
        case (us, i) =>  us.login == username}.map(_._2).getOrElse(-1)

      if (index >= 0) {
        if (passwords(index) == password)
          user
         else None
      } else None
    }

    for (user <- userOpt) {
      user.selected.clear()

      val files = Gallery.userFiles(user)

      user.selected ++= Selection.findAllBy(sqls.eq(Selection.c.email, user.login.trim.toLowerCase)).flatMap{
        selection =>
          val index = files.indexOf(selection.filename)
          if (index >= 0) {
            Seq(index)
          }
           else Seq.empty
      }

    }

    userOpt
  }

  def sha1(input: String) = {

    val digest = MessageDigest.getInstance("SHA-1")

    digest.update(input.getBytes, 0, input.length())

    new BigInteger(1, digest.digest()).toString(16)
  }

  val wmuaUser = User("WMUA", "WMUA", "1")

  val jury = Seq(
    User("***REMOVED***", "***REMOVED***", "1"),
    User("***REMOVED***" /*"***REMOVED***"*/, "***REMOVED***", "2"),
    User("***REMOVED***", "***REMOVED***", "3"),
    User("***REMOVED***" /*"***REMOVED***"*/, "***REMOVED***", "4"),
    User("***REMOVED***", "***REMOVED***", "5"),
    User("***REMOVED***" /*"***REMOVED***"*/, "***REMOVED***", "6"),
    User("***REMOVED***" , "***REMOVED***", "7"),
    User("***REMOVED***" /*"***REMOVED***"*/, "***REMOVED***", "8"),
    User("***REMOVED***" , "", "9"),
    User("***REMOVED***", "", "10"),
    User("***REMOVED***" , "***REMOVED***", "11"),
    User("***REMOVED***", "***REMOVED***", "12"),
    User("***REMOVED***", "", "13"),
    User("***REMOVED***", "***REMOVED***", "14"),
    User("***REMOVED***"/*"***REMOVED***"*/, "***REMOVED***", "15"),
    User("***REMOVED***" /*"***REMOVED***"*/, "***REMOVED***", "16")
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