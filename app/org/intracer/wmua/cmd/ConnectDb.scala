package org.intracer.wmua.cmd

import play.api.Play
import scalikejdbc.{ConnectionPool, GlobalSettings, LoggingSQLAndTimeSettings}

case class ConnectDb(host: String = "jury.wikilovesearth.org.ua") extends (() => Unit) {

  def apply() = {
    Class.forName("com.mysql.jdbc.Driver")

    val url = s"jdbc:mysql://$host/wlxjury"

    val user = Play.current.configuration.getString("db.default.user").get
    val password = Play.current.configuration.getString("db.default.user").get

    ConnectionPool.singleton(url, user, password)

    GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
      enabled = true,
      singleLineMode = false,
      printUnprocessedStackTrace = false,
      stackTraceDepth = 15,
      logLevel = 'info,
      warningEnabled = false,
      warningThresholdMillis = 3000L,
      warningLogLevel = 'warn
    )

  }

}