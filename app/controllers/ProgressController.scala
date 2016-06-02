package controllers

import org.atmosphere.config.service.{Disconnect, ManagedService, Ready}
import org.atmosphere.cpr.{AtmosphereResource, AtmosphereResourceEvent, Broadcaster}
import org.slf4j.{Logger, LoggerFactory}

@ManagedService(path = "/progress")
class ProgressController {
  var max: Long = 0

  private val logger: Logger = LoggerFactory.getLogger(classOf[ProgressController])

  private var broadcaster: Option[Broadcaster] = None

  @Ready
  def onReady(r: AtmosphereResource) {
    logger.info("Browser {} connected.", r.uuid)
    broadcaster = Some(r.getBroadcaster)
    Global.progressController = Some(this)
  }

  @Disconnect
  def onDisconnect(event: AtmosphereResourceEvent) {
    if (event.isCancelled) {
      logger.info("Browser {} unexpectedly disconnected", event.getResource.uuid)
    }
    else if (event.isClosedByClient) {
      logger.info("Browser {} closed the connection", event.getResource.uuid)
    }
  }

  def progress(percentage: Int) = {
    val percentStr = (percentage * 100.0 / max).toString

    logger.info(s":progress: $percentage * 100.0 / $max = $percentStr")

    broadcaster.map(_.broadcast(percentStr))
  }

}