// test/graphql2/SubscriptionSpec2.scala
package graphql2

import caliban.ResponseValue
import db.scalikejdbc.SharedTestDb
import graphql.SubscriptionEventBus
import graphql.SubscriptionEventBus.ImageRatedEvent
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import zio._
import zio.stream.ZStream

class SubscriptionSpec2 extends Specification with BeforeAll with GraphQL2SpecHelper {
  sequential

  override def beforeAll(): Unit = SharedTestDb.init()

  "imageRated subscription" should {
    "emit events matching the roundId" in {
      val bus = new SubscriptionEventBus()(testMat)
      val ctx = GraphQL2Context(None, bus, testMat)

      val collected = Unsafe.unsafe { implicit u =>
        Runtime.default.unsafe.run(
          SchemaBuilder.interpreter
            .execute("subscription { imageRated(roundId:\"42\") { roundId rate } }")
            .flatMap { resp =>
              resp.data match {
                case ResponseValue.ObjectValue(fields) =>
                  fields.collectFirst { case ("imageRated", stream) => stream } match {
                    case Some(ResponseValue.StreamValue(s)) =>
                      bus.publishImageRated(ImageRatedEvent(42L, 1L, 1L, 3))
                      s.take(1).runCollect
                    case _ => ZIO.succeed(Chunk.empty)
                  }
                case _ => ZIO.succeed(Chunk.empty)
              }
            }
            .provideLayer(ZLayer.succeed(ctx))
        ).getOrThrow()
      }

      collected must not(beEmpty)
    }
  }
}
