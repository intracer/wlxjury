package controllers

import org.intracer.wmua.{Contest, User}

class GenUsers {

}


object UkrainianJury {

  private lazy val ukraine: Contest = Contest.byId(14).head

  val users = Seq(
    new User("***REMOVED*** ***REMOVED***", "***REMOVED***", 0, User.JURY_ROLES, genPassword(), ukraine.id.toInt, Some("uk")),
    new User("***REMOVED*** ***REMOVED***", "***REMOVED***", 0, User.JURY_ROLES, genPassword(), ukraine.id.toInt, Some("uk")),
    new User("***REMOVED*** ***REMOVED***", "***REMOVED***", 0, User.JURY_ROLES, genPassword(), ukraine.id.toInt, Some("uk")),
    new User("***REMOVED*** ***REMOVED***", "***REMOVED***", 0, User.JURY_ROLES, genPassword(), ukraine.id.toInt, Some("uk")),
    new User("***REMOVED*** ***REMOVED***", "***REMOVED***", 0, User.JURY_ROLES, genPassword(), ukraine.id.toInt, Some("uk")),
    new User("***REMOVED*** ***REMOVED***", "***REMOVED***", 0, User.JURY_ROLES, genPassword(), ukraine.id.toInt, Some("uk")),
    new User("***REMOVED*** ***REMOVED***", "***REMOVED***", 0, User.JURY_ROLES, genPassword(), ukraine.id.toInt, Some("en")),
    new User("***REMOVED*** ***REMOVED***", "***REMOVED***", 0, User.JURY_ROLES, genPassword(), ukraine.id.toInt, Some("uk")),
    new User("***REMOVED*** ***REMOVED***", "***REMOVED***", 0, User.JURY_ROLES, genPassword(), ukraine.id.toInt, Some("uk")),
    new User("***REMOVED*** ***REMOVED***", "***REMOVED***", 0, User.JURY_ROLES, genPassword(), ukraine.id.toInt, Some("uk")),
    new User("***REMOVED***", "***REMOVED***", 0, User.JURY_ROLES, genPassword(), ukraine.id.toInt, Some("uk")),
    new User("***REMOVED*** ***REMOVED***", "***REMOVED***", 0, User.JURY_ROLES, genPassword(), ukraine.id.toInt, Some("uk")),
    new User("***REMOVED***", "***REMOVED***", 0, User.JURY_ROLES, genPassword(), ukraine.id.toInt, Some("en")),
    new User("***REMOVED***", "***REMOVED***", 0, User.JURY_ROLES, genPassword(), ukraine.id.toInt, Some("en"))
  )

  def genPassword(country: String = ukraine.country) = {
    Some(User.randomString(8))
  }


}
