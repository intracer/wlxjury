// app/graphql2/Subscriptions.scala
package graphql2

import caliban.CalibanError
import graphql2.inputs.ImageRatedArgs
import graphql2.views.ImageRatedEventView
import zio.stream.ZStream

case class Subscriptions(
  imageRated:    ImageRatedArgs => ZStream[GraphQL2Context, CalibanError, ImageRatedEventView],
  roundProgress: ImageRatedArgs => ZStream[GraphQL2Context, CalibanError, ImageRatedEventView]
)
