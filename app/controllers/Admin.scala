package controllers

import play.api.mvc.Controller
import org.intracer.wmua.{Image, Round, Contest, User}
import play.api.data.Form
import play.api.data.Forms._
import scala.concurrent.Await
import client.dto.{Page, PageQuery}
import org.joda.time.DateTime

object Admin extends Controller with Secured {

  val sendMail = new SendMail

  def users() = withAuth({
    username =>
      implicit request =>
        val user = User.byUserName(username)
        val users = User.findAll()

        Ok(views.html.users(user, users, editUserForm.copy(data = Map("roles" -> "jury")), Round.current(user)))
  }, Set(User.ADMIN_ROLE))

  def editUser(id: String) = withAuth({
    username =>
      implicit request =>
        val user = User.byUserName(username)
        val editedUser = User.find(id.toLong).get

        val filledForm = editUserForm.fill(editedUser)

        Ok(views.html.editUser(user, filledForm, Round.current(user)))
  }, Set(User.ADMIN_ROLE))

  def saveUser() = withAuth({
    username =>
      implicit request =>
        val user = User.byUserName(username)

        editUserForm.bindFromRequest.fold(
          formWithErrors => // binding failure, you retrieve the form containing errors,
            BadRequest(views.html.editUser(user, formWithErrors, Round.current(user))),
          value => {
            // binding success, you get the actual value
            val formUser = value
            val count: Long = User.countByEmail(formUser.id, formUser.email)
            if (count > 0) {
              BadRequest(views.html.editUser(user, editUserForm.fill(formUser).withError("email", "email should be unique"), Round.current(user)))
            } else {
              if (formUser.id == 0) {
                createNewUser(user, formUser)
              } else {
                User.updateUser(formUser.id, formUser.fullname, formUser.email, formUser.roles)
                for (password <- formUser.password) {
                  val hash = User.hash(formUser, password)
                  User.updateHash(formUser.id, hash)
                }
              }
              Redirect(routes.Admin.users)
            }
          }
        )
  }, Set(User.ADMIN_ROLE))

  def createNewUser(user: User, formUser: User): Boolean = {
    val password = User.randomString(8)
    val contest: Contest = Contest.byId(formUser.contest).head
    val hash = User.hash(formUser, password)
    val juryhome = "http://localhost:9000"
    User.create(formUser.fullname, formUser.email, hash, formUser.roles, formUser.contest)
    val subject: String = s"Welcome to ${contest.name} jury"
    val message: String = s"Organizing committee of ${contest.name} is glad to welcome you as a jury member.\n" +
      s" Please login to our jury tool $juryhome \nwith login: ${formUser.email} and password: $password.\n" +
      s"Regards, ${user.fullname}"
    sendMail.sendMail(from = (user.fullname, user.email), to = Seq(formUser.email), bcc = Seq(user.email), subject = subject, message = message)
  }

  val editUserForm = Form(
    mapping(
      "id" -> longNumber(),
      "fullname" -> nonEmptyText(),
      "email" -> email,
      "password" -> optional(text()),
      "roles" -> nonEmptyText(),
      "contest" -> number
    )(User.applyEdit)(User.unapplyEdit)
  )

  val imagesForm = Form("images" -> optional(text()))

  val selectRoundForm = Form("currentRound" -> text())

  val editRoundForm = Form(
    mapping(
      "id" -> longNumber(),
      "number" -> number(),
      "name" -> optional(text()),
      "contest" -> number,
      "roles" -> text(),
      "distribution" -> number,
      "rates" -> number,
      "limitMin" -> number,
      "limitMax" -> number,
      "recommended" -> optional(number)
    )(Round.applyEdit)(Round.unapplyEdit)
  )

  def rounds() = withAuth({
    username =>
      implicit request =>
        val user = User.byUserName(username)
        val rounds = Round.findByContest(user.contest)
        val contest = Contest.byId(user.contest).get

        Ok(views.html.rounds(user, rounds, editRoundForm,
          imagesForm.fill(Some(contest.getImages)),
          selectRoundForm.fill(contest.currentRound.toString),
          Round.current(user)))
  }, Set(User.ADMIN_ROLE))


  def setRound() = withAuth({
    username =>
      implicit request =>
        val user = User.byUserName(username)

        val newRound = selectRoundForm.bindFromRequest.get

        Contest.setCurrentRound(user.contest, newRound.toInt)

        Redirect(routes.Admin.rounds())
  }, Set(User.ADMIN_ROLE))


  def distributeImages(contest: Contest) {
    for (round <- Round.find(contest.currentRound)) {

      val images = round.allImages
      val jurors = round.jurors

      val selection: Seq[Selection] = round.distribution match {
        case 0 =>
          jurors.flatMap { juror =>
            images.map(img => new Selection(0, img.pageId, 0, juror.id, round.id, DateTime.now))
          }
        case 1 =>
          val perJuror = images.size / jurors.size
          jurors.zipWithIndex.flatMap { case (juror, i) =>
            images.slice(i * perJuror, (i + 1) * perJuror).map(img => new Selection(0, img.pageId, 0, juror.id, round.id, DateTime.now))
          }

        case 2 =>
          val imagesTwice = images ++ images
          val perJuror = imagesTwice.size / jurors.size
          jurors.zipWithIndex.flatMap { case (juror, i) =>
            imagesTwice.slice(i * perJuror, (i + 1) * perJuror).map(img => new Selection(0, img.pageId, 0, juror.id, round.id, DateTime.now))
          }
      }
      Selection.batchInsert(selection)
    }
  }

  def editRound(id: String) = withAuth({
    username =>
      implicit request =>
        val user = User.byUserName(username)
        val round = Round.find(id.toLong).get

        val filledRound = editRoundForm.fill(round)

        Ok(views.html.editRound(user, filledRound, Round.current(user)))
  }, Set(User.ADMIN_ROLE))

  def saveRound() = withAuth({
    username =>
      implicit request =>
        val user = User.byUserName(username)

        editRoundForm.bindFromRequest.fold(
          formWithErrors => // binding failure, you retrieve the form containing errors,
            BadRequest(views.html.editRound(user, formWithErrors, Round.current(user))),
          round => {
            if (round.id == 0) {
              createNewRound(user, round)
            } else {
              Round.updateRound(round.id, round)
            }
            Redirect(routes.Admin.rounds())
          }
        )
  }, Set(User.ADMIN_ROLE))

  def createNewRound(user: User, round: Round): Round = {

    //  val contest: Contest = Contest.byId(round.contest).head

    val count = Round.countByContest(round.contest)

    Round.create(count + 1, round.name, round.contest, round.roles.head, round.distribution, round.rates.id, round.limitMin, round.limitMax, round.recommended)

  }

  def setImages() = withAuth({
    username =>
      implicit request =>
        val user = User.byUserName(username)
        val imagesSource: Option[String] = imagesForm.bindFromRequest.get
        val contest = Contest.byId(user.contest).get
        Contest.updateImages(contest.id, imagesSource)

        import scala.concurrent.duration._

        //val images: Seq[Page] = Await.result(Global.commons.categoryMembers(PageQuery.byTitle(imagesSource.get)), 1.minute)

        distributeImages(contest)

        //        Round.up

        Redirect(routes.Admin.rounds())

  }, Set(User.ADMIN_ROLE))

  def resetPassword(id: String) = withAuth({
    username =>
      implicit request =>
        val user = User.byUserName(username)
        val editedUser = User.find(id.toLong).get

        val password = User.randomString(8)
        val contest: Contest = Contest.byId(editedUser.contest).head
        val hash = User.hash(editedUser, password)

        User.updateHash(editedUser.id, hash)

        val juryhome = "http://localhost:9000"
//        User.updateUser(formUser.fullname, formUser.email, hash, formUser.roles, formUser.contest)
        val subject: String = s"Password changed for ${contest.name} jury"
        val message: String = s"Password changed for ${contest.name} jury\n" +
          s" Please login to our jury tool $juryhome \nwith login: ${editedUser.email} and password: $password.\n" +
          s"Regards, ${user.fullname}"
        sendMail.sendMail(from = (user.fullname, user.email), to = Seq(editedUser.email), bcc = Seq(user.email), subject = subject, message = message)

        Redirect(routes.Admin.editUser(id)).flashing("password-reset" -> s"Password reset. New Password sent to ${editedUser.email}")

  }, Set(User.ADMIN_ROLE))


}
