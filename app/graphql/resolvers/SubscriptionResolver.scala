// app/graphql/resolvers/SubscriptionResolver.scala
package graphql.resolvers

import graphql.GraphQLContext
import sangria.schema._

object SubscriptionResolver {
  // Stub fields — replaced with streaming implementation in Task 14
  val imageRatedField: Field[GraphQLContext, Unit] = Field(
    "imageRated", StringType,
    resolve = _ => "stub"
  )

  val roundProgressField: Field[GraphQLContext, Unit] = Field(
    "roundProgress", StringType,
    resolve = _ => "stub"
  )
}
