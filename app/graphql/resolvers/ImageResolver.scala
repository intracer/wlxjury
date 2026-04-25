package graphql.resolvers

import graphql.GraphQLContext
import org.intracer.wmua.{Image, ImageWithRating, Selection}
import sangria.schema._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ImageResolver {

  lazy val ImageType: ObjectType[GraphQLContext, Image] = ObjectType(
    "Image",
    fields[GraphQLContext, Image](
      Field("pageId",      StringType,              resolve = i => i.value.pageId.toString),
      Field("title",       StringType,              resolve = _.value.title),
      Field("url",         OptionType(StringType),  resolve = _.value.url),
      Field("pageUrl",     OptionType(StringType),  resolve = _.value.pageUrl),
      Field("width",       IntType,                 resolve = _.value.width),
      Field("height",      IntType,                 resolve = _.value.height),
      Field("monumentId",  OptionType(StringType),  resolve = _.value.monumentId),
      Field("author",      OptionType(StringType),  resolve = _.value.author),
      Field("mime",        OptionType(StringType),  resolve = _.value.mime),
      Field("isVideo",     BooleanType,             resolve = i => i.value.mime.exists(_.startsWith("video")))
    )
  )

  lazy val SelectionType: ObjectType[GraphQLContext, Selection] = ObjectType(
    "Selection",
    fields[GraphQLContext, Selection](
      Field("id",          OptionType(StringType),  resolve = s => s.value.id.map(_.toString)),
      Field("pageId",      StringType,              resolve = s => s.value.pageId.toString),
      Field("juryId",      StringType,              resolve = s => s.value.juryId.toString),
      Field("roundId",     StringType,              resolve = s => s.value.roundId.toString),
      Field("rate",        IntType,                 resolve = _.value.rate),
      Field("criteriaId",  OptionType(IntType),     resolve = _.value.criteriaId),
      Field("monumentId",  OptionType(StringType),  resolve = _.value.monumentId),
      Field("createdAt",   OptionType(StringType),  resolve = s => s.value.createdAt.map(_.toString))
    )
  )

  lazy val ImageWithRatingType: ObjectType[GraphQLContext, ImageWithRating] = ObjectType(
    "ImageWithRating",
    fields[GraphQLContext, ImageWithRating](
      Field("image",       ImageType,               resolve = _.value.image),
      Field("selections",  ListType(SelectionType), resolve = i => i.value.selection.toList),
      Field("totalRate",   FloatType,               resolve = _.value.rateSum.toDouble),
      Field("rateSum",     IntType,                 resolve = _.value.rateSum),
      Field("rank",        OptionType(IntType),     resolve = _.value.rank)
    )
  )

  val imageField: Field[GraphQLContext, Unit] = Field(
    "image", OptionType(ImageType),
    arguments = Argument("pageId", IDType) :: Nil,
    resolve = c => Future.successful(None)
  )

  val imagesField: Field[GraphQLContext, Unit] = Field(
    "images", ListType(ImageWithRatingType),
    arguments =
      Argument("roundId",  IDType) ::
      Argument("userId",   OptionInputType(IDType)) ::
      Argument("page",     OptionInputType(IntType)) ::
      Argument("pageSize", OptionInputType(IntType)) ::
      Argument("rate",     OptionInputType(IntType)) ::
      Argument("region",   OptionInputType(StringType)) :: Nil,
    resolve = _ => Future.successful(Nil)
  )
}
