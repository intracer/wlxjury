package org.intracer.wmua.cmd

import scalikejdbc.{LoggingSQLAndTimeSettings, GlobalSettings, ConnectionPool}

case class ConnectDb(host: String = "jury.wikilovesearth.org.ua") extends (() => Unit) {

  def apply() = {
    Class.forName("com.mysql.jdbc.Driver")

    val url = s"jdbc:mysql://$host/wlxjury"

    ConnectionPool.singleton(url, "***REMOVED***", "***REMOVED***")

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