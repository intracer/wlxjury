package db.scalikejdbc

import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

import com.wix.mysql.EmbeddedMysql.anEmbeddedMysql
import com.wix.mysql.config.DownloadConfig.aDownloadConfig
import com.wix.mysql.config.MysqldConfig.aMysqldConfig
import com.wix.mysql.distribution.Version.v5_7_latest
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.running

trait InMemDb {

  def now = ZonedDateTime.now.withNano(0)

  def inMemDbApp[T](block: Application => T)
                   (implicit additionalConfig: Map[String, String] = Map.empty): T = {
    val downloadConfig = aDownloadConfig()
      .withCacheDir(System.getProperty("user.home") + "/.wixMySQL/downloads")
      .build()
    val config = aMysqldConfig(v5_7_latest)
      .withFreePort()
      .withUser("WLXJURY_DB_USER", "WLXJURY_DB_PASSWORD")
      .withTimeout(60, TimeUnit.SECONDS)
      .build()
    val mysqld = anEmbeddedMysql(config, downloadConfig)
      .addSchema("wlxjury")
      .start()

    val port = mysqld.getConfig.getPort
    val fakeApp = {
      val dbConfiguration = Map(
        "db.default.username" -> "WLXJURY_DB_USER",
        "db.default.password" -> "WLXJURY_DB_PASSWORD",
        "db.default.url" -> s"jdbc:mysql://localhost:$port/wlxjury?autoReconnect=true&autoReconnectForPools=true&useUnicode=true&characterEncoding=UTF-8&useSSL=false"
      )

      new GuiceApplicationBuilder()
        .configure(dbConfiguration ++ additionalConfig)
        .bindings(new scalikejdbc.PlayModule)
        .build
    }

    try {
      running(fakeApp)(block(fakeApp))
    } finally {
      mysqld.stop()
    }
  }

  def inMemDb[T](block: => T)
                (implicit additionalConfig: Map[String, String] = Map.empty): T = {
    inMemDbApp(_ => block)
  }
}