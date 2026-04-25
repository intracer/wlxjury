package graphql2.views

import db.scalikejdbc.User

case class UserView(
  id:          String,
  fullname:    String,
  email:       String,
  roles:       List[String],
  contestId:   Option[String],
  lang:        Option[String],
  wikiAccount: Option[String],
  active:      Option[Boolean]
)

object UserView {
  def from(u: User): UserView = UserView(
    id          = u.id.fold("")(_.toString),
    fullname    = u.fullname,
    email       = u.email,
    roles       = u.roles.toList,
    contestId   = u.contestId.map(_.toString),
    lang        = u.lang,
    wikiAccount = u.wikiAccount,
    active      = u.active
  )
}
