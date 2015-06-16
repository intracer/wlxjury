package controllers

import db.scalikejdbc.{ContestJuryJdbc, RoundJdbc, UserJdbc}
import org.intracer.wmua._
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages.Implicits._
import play.api.i18n.{Lang, Messages}
import play.api.mvc.Controller
import play.api.mvc.Results._
import play.cache.Cache

object Admin extends Controller with Secured {

  val sendMail = new SendMail

  def users() = withAuth({
    user =>
      implicit request =>
        val users = UserJdbc.findByContest(user.contest)

        Ok(views.html.users(user, users, editUserForm.copy(data = Map("roles" -> "jury")), RoundJdbc.current(user)))
  }, Set(User.ADMIN_ROLE))

  def editUser(id: String) = withAuth({
    user =>
      implicit request =>
        val editedUser = UserJdbc.find(id.toLong).get

        val filledForm = editUserForm.fill(editedUser)

        Ok(views.html.editUser(user, filledForm, RoundJdbc.current(user)))
  }, Set(User.ADMIN_ROLE, s"USER_ID_$id"))

  def saveUser() = withAuth({
    user =>
      implicit request =>

        editUserForm.bindFromRequest.fold(
          formWithErrors => // binding failure, you retrieve the form containing errors,
            BadRequest(views.html.editUser(user, formWithErrors, RoundJdbc.current(user))),
          formUser => {
            val userId = formUser.id.get
            if (!(user.roles.contains(User.ADMIN_ROLE) || user.id == formUser.id)) {
              Redirect(routes.Login.index())
            }

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
                if (!user.roles.contains(User.ADMIN_ROLE)) {
                  val origUser = UserJdbc.find(formUser.id.get).get
                  UserJdbc.updateUser(userId, formUser.fullname, formUser.email, origUser.roles, formUser.lang)
                } else {
                  UserJdbc.updateUser(userId, formUser.fullname, formUser.email, formUser.roles, formUser.lang)
                }
                for (password <- formUser.password) {
                  val hash = UserJdbc.hash(formUser, password)
                  UserJdbc.updateHash(userId, hash)
                }


              }
              Cache.remove(s"user/${user.email}")
              val result = Redirect(routes.Admin.users)
              val lang = for (lang <- formUser.lang; if formUser.id == user.id) yield lang

              lang.fold(result)(l => result.withLang(Lang(l)))
            }
          }
        )
  })

  def createNewUser(user: User, formUser: User): Unit = {
    val contest: ContestJury = ContestJuryJdbc.byId(formUser.contest)
    createUser(user, formUser, contest)
  }

  def createUser(user: User, formUser: User, contest: ContestJury) {
    val password = UserJdbc.randomString(8)
    val hash = UserJdbc.hash(formUser, password)
    val juryhome = "http://wlxjury.wikimedia.in.ua"
    UserJdbc.create(formUser.fullname, formUser.email, hash, formUser.roles, formUser.contest, formUser.lang)
    implicit val lang = formUser.lang.fold(Lang("en"))(Lang.apply)
    val subject: String = Messages("welcome.subject", Messages(contest.name))
    val message: String = Messages("welcome.messsage", Messages(contest.name), juryhome, formUser.email, password, user.fullname)
    sendMail.sendMail(from = (user.fullname, user.email), to = Seq(user.email), bcc = Seq(user.email), subject = subject, message = message)
  }

  val editUserForm = Form(
    mapping(
      "id" -> longNumber(),
      "fullname" -> nonEmptyText(),
      "email" -> email,
      "password" -> optional(text()),
      "roles" -> optional(text()),
      "contest" -> longNumber,
      "lang" -> optional(text())
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
        val contest: ContestJury = ContestJuryJdbc.byId(editedUser.contest)
        val hash = UserJdbc.hash(editedUser, password)

        UserJdbc.updateHash(editedUser.id.get, hash)

        val juryhome = "http://localhost:9000"
        //        User.updateUser(formUser.fullname, formUser.email, hash, formUser.roles, formUser.contest)
        val subject: String = s"Password changed for ${contest.name} jury"
        val message: String = s"Password changed for ${contest.name} jury\n" +
          s" Please login to our jury tool $juryhome \nwith login: ${editedUser.email} and password: $password\n" +
          s"Regards, ${user.fullname}"
         sendMail.sendMail(from = (user.fullname, user.email), to = Seq(user.email), bcc = Seq(user.email), subject = subject, message = message)

        Redirect(routes.Admin.editUser(id)).flashing("password-reset" -> s"Password reset. New Password sent to ${editedUser.email}")

  }, Set(User.ADMIN_ROLE))


}
