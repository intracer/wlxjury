// app/graphql2/Mutations.scala
package graphql2

import caliban.CalibanError
import graphql2.inputs._
import graphql2.views._
import zio._

case class Mutations(
  login:            LoginArgs            => ZIO[GraphQL2Context, CalibanError, AuthPayloadView],
  createContest:    CreateContestArgs    => ZIO[GraphQL2Context, CalibanError, ContestView],
  updateContest:    UpdateContestArgs    => ZIO[GraphQL2Context, CalibanError, ContestView],
  deleteContest:    DeleteContestArgs    => ZIO[GraphQL2Context, CalibanError, Boolean],
  createRound:      CreateRoundArgs      => ZIO[GraphQL2Context, CalibanError, RoundView],
  updateRound:      UpdateRoundArgs      => ZIO[GraphQL2Context, CalibanError, RoundView],
  setActiveRound:   SetActiveRoundArgs   => ZIO[GraphQL2Context, CalibanError, RoundView],
  addJurorsToRound: AddJurorsArgs        => ZIO[GraphQL2Context, CalibanError, RoundView],
  rateImage:        RateImageArgs        => ZIO[GraphQL2Context, CalibanError, SelectionView],
  rateImageBulk:    RateImageBulkArgs    => ZIO[GraphQL2Context, CalibanError, List[SelectionView]],
  setRoundImages:   SetRoundImagesArgs   => ZIO[GraphQL2Context, CalibanError, Boolean],
  createUser:       CreateUserArgs       => ZIO[GraphQL2Context, CalibanError, UserView],
  updateUser:       UpdateUserArgs       => ZIO[GraphQL2Context, CalibanError, UserView],
  addComment:       AddCommentArgs       => ZIO[GraphQL2Context, CalibanError, CommentView]
)
