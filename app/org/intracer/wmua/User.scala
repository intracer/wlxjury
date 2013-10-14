package org.intracer.wmua

import java.security.MessageDigest
import java.math.BigInteger
import scala.collection.mutable

case class User(fullname: String, login: String, hash: String,
                selected:collection.mutable.SortedSet[Int] = collection.mutable.SortedSet[Int](),
                files: mutable.Buffer[String] = mutable.Buffer.empty) {

}


object User {
  def login(username: String, password: String): Option[User] = {
    if (sha1(username + "/" + password) == "***REMOVED***") {
      Some(wmuaUser)
    } else {
      byUserName.get(username)
    }
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

  val byUserName = (orgCom ++ jury ++ Seq(wmuaUser)).map(user => (user.login, user)).toMap

  val passwords = Seq("***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***",
    "***REMOVED***")

}