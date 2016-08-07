package org.intracer.wmua

import org.scalawiki.MwBot
import org.specs2.mock.Mockito

trait JuryTestHelpers extends Mockito {

  def mockBot(): MwBot = {
    val bot = mock[MwBot]
    bot.log returns MwBot.system.log
    bot.system returns MwBot.system
    bot
  }

}
