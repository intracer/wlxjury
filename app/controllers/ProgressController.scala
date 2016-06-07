package controllers

import org.atmosphere.config.service.{Disconnect, ManagedService, PathParam, Ready}
import org.atmosphere.cpr.{AtmosphereResource, AtmosphereResourceEvent, Broadcaster}
import org.slf4j.{Logger, LoggerFactory}

@ManagedService(path = "/progress/{contestId: [0-9]*}")
class ProgressController {

  private val logger: Logger = LoggerFactory.getLogger(classOf[ProgressController])

  private var broadcaster: Option[Broadcaster] = None

  @PathParam("contestId")
  private val contestId: String = "22"

  @Ready
  def onReady(r: AtmosphereResource) {
    logger.info("Browser {} connected.", r.uuid)
    broadcaster = Some(r.getBroadcaster)
    Global.progressControllers.putIfAbsent(contestId, this)
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

  def progress(done: Int, max: Int) = {
    val percentStr = (done * 100.0 / max).toString

    logger.info(s":progress: $done * 100.0 / $max = $percentStr")

    broadcaster.map(_.broadcast(percentStr))
  }

}