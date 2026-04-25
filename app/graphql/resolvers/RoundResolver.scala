// app/graphql/resolvers/RoundResolver.scala
package graphql.resolvers

import db.scalikejdbc.Round
import graphql.GraphQLContext
import sangria.schema._

object RoundResolver {

  lazy val RoundType: ObjectType[GraphQLContext, Round] = ObjectType(
    "Round",
    () => fields[GraphQLContext, Round](
      Field("id",          StringType,             resolve = r => r.value.id.fold("")(_.toString)),
      Field("number",      LongType,               resolve = _.value.number),
      Field("name",        OptionType(StringType), resolve = _.value.name),
      Field("contestId",   StringType,             resolve = r => r.value.contestId.toString),
      Field("active",      BooleanType,            resolve = _.value.active),
      Field("hasCriteria", BooleanType,            resolve = _.value.hasCriteria),
      Field("mediaType",   OptionType(StringType), resolve = _.value.mediaType),
      Field("category",    OptionType(StringType), resolve = _.value.category),
      Field("regions",     OptionType(StringType), resolve = _.value.regions),
      Field("minMpx",      OptionType(IntType),    resolve = _.value.minMpx)
    )
  )
}
