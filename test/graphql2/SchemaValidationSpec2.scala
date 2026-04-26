// test/graphql2/SchemaValidationSpec2.scala
package graphql2

import org.specs2.mutable.Specification

class SchemaValidationSpec2 extends Specification {

  "SchemaBuilder.api SDL" should {
    "contain all expected type names" in {
      val sdl = SchemaBuilder.api.render
      sdl must contain("ContestView")
      sdl must contain("RoundView")
      sdl must contain("UserView")
      sdl must contain("ImageView")
      sdl must contain("SelectionView")
      sdl must contain("ImageWithRatingView")
      sdl must contain("CommentView")
      sdl must contain("MonumentView")
      sdl must contain("AuthPayloadView")
      sdl must contain("ImageRatedEventView")
      sdl must contain("RoundStatView")
    }

    "contain all expected query fields" in {
      val sdl = SchemaBuilder.api.render
      sdl must contain("contests")
      sdl must contain("contest")
      sdl must contain("round")
      sdl must contain("rounds")
      sdl must contain("roundStat")
      sdl must contain("images")
      sdl must contain("image")
      sdl must contain("users")
      sdl must contain("user")
      sdl must contain("me")
      sdl must contain("comments")
      sdl must contain("monuments")
      sdl must contain("monument")
    }

    "contain all expected mutation fields" in {
      val sdl = SchemaBuilder.api.render
      sdl must contain("login")
      sdl must contain("createContest")
      sdl must contain("updateContest")
      sdl must contain("deleteContest")
      sdl must contain("createRound")
      sdl must contain("updateRound")
      sdl must contain("setActiveRound")
      sdl must contain("addJurorsToRound")
      sdl must contain("rateImage")
      sdl must contain("rateImageBulk")
      sdl must contain("setRoundImages")
      sdl must contain("createUser")
      sdl must contain("updateUser")
      sdl must contain("addComment")
    }

    "contain subscription fields" in {
      val sdl = SchemaBuilder.api.render
      sdl must contain("imageRated")
      sdl must contain("roundProgress")
    }
  }
}
