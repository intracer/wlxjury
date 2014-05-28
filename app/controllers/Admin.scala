package controllers

import play.api.mvc.{Security, Controller}
import org.intracer.wmua.User
import play.api.data.Form
import play.api.data.Forms._

object Admin extends Controller with Secured {

  val sendMail = new SendMail

  def users() = withAuth {
    username =>
      implicit request =>
        val user = User.byUserName.get(username.trim).get
        val users = User.findAll()

        Ok(views.html.users(user, users, editUserForm.copy(data = Map("roles" -> "jury"))))
  }

  def editUser(id: String) = withAuth {
    username =>
      implicit request =>
        val user = User.byUserName.get(username.trim).get
        val editedUser = User.find(id.toLong).get

        val filledForm = editUserForm.fill(editedUser)

        Ok(views.html.editUser(user, filledForm))
  }

  def saveUser() = withAuth {
    username =>
      implicit request =>
        val user = User.byUserName.get(username.trim).get
        val users = User.findAll()

        editUserForm.bindFromRequest.fold(
          formWithErrors => // binding failure, you retrieve the form containing errors,
            BadRequest(views.html.editUser(user, formWithErrors)),
          value => {
            // binding success, you get the actual value
            val formUser = value
            val count: Long = User.countByEmail(formUser.id, formUser.email)
            if (count > 0) {
              BadRequest(views.html.editUser(user, editUserForm.fill(formUser).withError("email", "email should be unique")))
            } else {
              if (formUser.id == 0) {
                User.create(formUser.fullname, formUser.email, User.randomString(8), formUser.roles)
                sendMail.sendMail(from = (user.fullname, user.email), to = Seq(formUser.email), subject = "Hi", message = "Putin huylo")
              } else {
                User.updateUser(formUser.id, formUser.fullname, formUser.email, formUser.roles)
              }
              Redirect(routes.Admin.users)
            }
          }
        )
  }


  val editUserForm = Form(
    mapping(
      "id" -> longNumber(),
      "fullname" -> nonEmptyText(),
      "email" -> email,
      "password" -> optional(text()),
      "roles" -> nonEmptyText()
    )(User.applyEdit)(User.unapplyEdit)
  )


}
