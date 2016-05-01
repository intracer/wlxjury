package controllers

import db.scalikejdbc.{ContestJuryJdbc, RoundJdbc, UserJdbc}
import org.intracer.wmua._
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages.Implicits._
import play.api.i18n.{Lang, Messages}
import play.api.mvc.{Controller, Result}
import play.api.mvc.Results._
import play.cache.Cache

object Admin extends Controller with Secured {

  val sendMail = new SendMail

  def users(contestId: Option[Long] = None) = withAuth({
    user =>
      implicit request =>
        val users = user.currentContest.orElse(contestId).fold(Seq.empty[User])(UserJdbc.findByContest)

        Ok(views.html.users(user, users, editUserForm.copy(data = Map("roles" -> "jury")), RoundJdbc.current(user)))
  }, User.ADMIN_ROLES)

  def havingEditRights(currentUser: User, otherUser: User)(block: => Result): Result = {
    if (!currentUser.canEdit(otherUser)) {
      Redirect(routes.Login.index()) // TODO message
    } else {
      block
    }
  }

  def editUser(id: String) = withAuth({
    user =>
      implicit request =>

        val editedUser = UserJdbc.find(id.toLong).get

        havingEditRights(user, editedUser) {

          val filledForm = editUserForm.fill(editedUser)

          Ok(views.html.editUser(user, filledForm, RoundJdbc.current(user)))
        }
  }, Set(User.ADMIN_ROLE, User.ROOT_ROLE, s"USER_ID_$id"))

  def saveUser() = withAuth({
    user =>
      implicit request =>

        editUserForm.bindFromRequest.fold(
          formWithErrors => // binding failure, you retrieve the form containing errors,
            BadRequest(views.html.editUser(user, formWithErrors, RoundJdbc.current(user))),
          formUser => {
            havingEditRights(user, formUser) {

              val userId = formUser.id.get
              val count: Long = UserJdbc.countByEmail(userId, formUser.email)
              if (count > 0) {
                BadRequest(
                  views.html.editUser(
                    user,
                    editUserForm.fill(formUser).withError("email", "email should be unique"),
                    RoundJdbc.current(user)
                  )
                )
              } else {
                if (userId == 0) {
                  createNewUser(user, formUser)
                } else {

                  // only admin can update roles
                  val newRoles = if (user.hasAnyRole(User.ADMIN_ROLES)) {
                    formUser.roles
                  } else {
                    val origUser = UserJdbc.find(formUser.id.get).get
                    origUser.roles
                  }

                  UserJdbc.updateUser(userId, formUser.fullname, formUser.email, newRoles, formUser.lang)

                  for (password <- formUser.password) {
                    val hash = UserJdbc.hash(formUser, password)
                    UserJdbc.updateHash(userId, hash)
                  }
                }
                Cache.remove(s"user/${user.email}")

                val result = Redirect(routes.Admin.users(user.contest))
                val lang = for (lang <- formUser.lang; if formUser.id == user.id) yield lang

                lang.fold(result)(l => result.withLang(Lang(l)))
              }
            }
          }
        )
  })

  def createNewUser(user: User, formUser: User): Unit = {
    val contest: Option[ContestJury] = formUser.currentContest.flatMap(ContestJuryJdbc.byId)
    createUser(user, formUser, contest)
  }

  def createUser(user: User, formUser: User, contest: Option[ContestJury]) {
    val password = UserJdbc.randomString(8)
    val hash = UserJdbc.hash(formUser, password)
    UserJdbc.create(formUser.fullname, formUser.email, hash, formUser.roles, formUser.contest, formUser.lang)
  }

  def sendMail(user: User, contest: Option[ContestJury], password: String) = {
    val juryhome = "http://wlxjury.wikimedia.in.ua"
    implicit val lang = user.lang.fold(Lang("en"))(Lang.apply)
    val contestName = contest.fold("")(c => Messages(c.name))
    val subject = Messages("welcome.subject", contestName)
    val message = Messages("welcome.messsage", contestName, juryhome, user.email, password, user.fullname)
    //sendMail.sendMail(from = (user.fullname, user.email), to = Seq(user.email), bcc = Seq(user.email), subject = subject, message = message)
  }

  val editUserForm = Form(
    mapping(
      "id" -> longNumber,
      "fullname" -> nonEmptyText,
      "email" -> email,
      "password" -> optional(text),
      "roles" -> optional(text),
      "contest" -> optional(longNumber),
      "lang" -> optional(text)
    )(User.applyEdit)(User.unapplyEdit)
  )

  //  def rounds() = withAuth({
  //    user =>
  //      implicit request =>
  //        val rounds = Round.findByContest(user.contest)
  //        val contest = ContestJury.byId(user.contest).get
  //
  //        Ok(views.html.rounds(user, rounds, editRoundForm,
  //          imagesForm.fill(Some(contest.getImages)),
  //          selectRoundForm.fill(contest.currentRound.toString),
  //          Round.current(user)))
  //  }, Set(User.ADMIN_ROLE))


  def resetPassword(id: String) = withAuth({
    user =>
      implicit request =>
        val editedUser = UserJdbc.find(id.toLong).get

        val password = UserJdbc.randomString(8)
        val contest: Option[ContestJury] = editedUser.currentContest.flatMap(ContestJuryJdbc.byId)
        val contestName = contest.fold("")(_.name)
        val hash = UserJdbc.hash(editedUser, password)

        UserJdbc.updateHash(editedUser.id.get, hash)

        val juryhome = "http://localhost:9000"
        //        User.updateUser(formUser.fullname, formUser.email, hash, formUser.roles, formUser.contest)
        val subject: String = s"Password changed for $contestName jury"
        val message: String = s"Password changed for $contestName jury\n" +
          s" Please login to our jury tool $juryhome \nwith login: ${editedUser.email} and password: $password\n" +
          s"Regards, ${user.fullname}"
        // sendMail.sendMail(from = (user.fullname, user.email), to = Seq(user.email), bcc = Seq(user.email), subject = subject, message = message)

        Redirect(routes.Admin.editUser(id)).flashing("password-reset" -> s"Password reset. New Password sent to ${editedUser.email}")

  }, User.ADMIN_ROLES)


}
