// app/graphql2/Queries.scala
package graphql2

import caliban.CalibanError
import graphql2.inputs._
import graphql2.views._
import zio._

case class Queries(
  contests:  ZIO[GraphQL2Context, CalibanError, List[ContestView]],
  contest:   ContestArgs          => ZIO[GraphQL2Context, CalibanError, Option[ContestView]],
  round:     RoundByIdArgs        => ZIO[GraphQL2Context, CalibanError, Option[RoundView]],
  rounds:    RoundsByContestArgs  => ZIO[GraphQL2Context, CalibanError, List[RoundView]],
  roundStat: RoundStatArgs        => ZIO[GraphQL2Context, CalibanError, RoundStatView],
  images:    ImagesArgs           => ZIO[GraphQL2Context, CalibanError, List[ImageWithRatingView]],
  image:     ImageArgs            => ZIO[GraphQL2Context, CalibanError, Option[ImageView]],
  users:     UsersArgs            => ZIO[GraphQL2Context, CalibanError, List[UserView]],
  user:      UserByIdArgs         => ZIO[GraphQL2Context, CalibanError, Option[UserView]],
  me:        ZIO[GraphQL2Context, Nothing, Option[UserView]],
  comments:  CommentsArgs         => ZIO[GraphQL2Context, CalibanError, List[CommentView]],
  monuments: MonumentsArgs        => ZIO[GraphQL2Context, CalibanError, List[MonumentView]],
  monument:  MonumentArgs         => ZIO[GraphQL2Context, CalibanError, Option[MonumentView]]
)
