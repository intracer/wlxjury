package org.intracer.wmua.cmd

import db.scalikejdbc.{ContestJuryJdbc, UserJdbc}

case class AddUsers(
                     contestId: Long,
                     role: String,
                     toNumber: Int,
                     usernameFun: (String, Int) => String)
  extends (() => Unit) {


  def apply() = {
    val contest = ContestJuryJdbc.find(contestId).get

    val country = contest.country.replaceAll("[ \\-\\&]", "")

    val existingUsers = UserJdbc.findByContest(contestId).filter(_.roles.contains(role))

    val start = existingUsers.size + 1
    val range = start to toNumber

    println(s"Contest with id $contestId, name ${contest.name}, country ${contest.country}, year ${contest.year}" )
    println(s"Users with role $role in contest $contest. Existing: $existingUsers, going to create: ${range.size} to have $toNumber")

    val logins = range.map(i => usernameFun(country, i))

    println(s"New logins: ${logins.mkString(", ")}")

    val passwords = logins.map(s => UserJdbc.randomString(8))

    val users = logins.zip(passwords).map {
      case (login, password) =>
        UserJdbc.create(
          login,
          login,
          UserJdbc.sha1(contest.country + "/" + password),
          Set(role),
          contest.id,
          Some("en")
        )
    }

    val total = UserJdbc.findByContest(contestId).filter(_.roles.contains(role))
    require (total.size == toNumber, s"Only ${total.size} users in DB, $toNumber expected")

    println(s"Successfully created ${users.size} users")
    println("login / password")

    logins.zip(passwords).foreach {
      case (login, password) =>
        println(s"$login / $password")
    }
  }

}
