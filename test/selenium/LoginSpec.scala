package selenium

import com.whisk.docker.impl.spotify.DockerKitSpotify
import com.whisk.docker.specs2.DockerTestKit
import docker.DockerMysqlService
import play.api.test.PlaySpecification

class LoginSpec extends PlaySpecification with DockerTestKit with DockerKitSpotify with DockerMysqlService {

  "login" should {
    "login" in {
      ok
    }
  }

}
