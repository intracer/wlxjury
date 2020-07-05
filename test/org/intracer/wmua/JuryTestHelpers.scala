package org.intracer.wmua

import org.scalawiki.MwBot
import org.specs2.mock.Mockito

import scala.io.{Codec, Source}

trait JuryTestHelpers extends Mockito {

  def mockBot(): MwBot = {
    val bot = mock[MwBot]
    bot.log returns MwBot.system.log
    bot.system returns MwBot.system
    bot
  }

  def resourceAsString(resource: String): String = {
    val is = getClass.getClassLoader.getResourceAsStream(resource)
    Source.fromInputStream(is)(Codec.UTF8).mkString
  }

}
