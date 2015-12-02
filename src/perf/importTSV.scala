package default

import scala.concurrent.duration._
import java.io._

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.jdbc.Predef._

class importTSV extends Simulation {

  val accessToken = "YOUR_ACCESS_TOKEN" //place your token here :)

  val httpProtocol = http
    .baseURL("https://firecloud.dsde-dev.broadinstitute.org") //hit orchestration instead of rawls. this functionality doesn't quite exist in rawls
    .inferHtmlResources()

  val headers = Map("Authorization" -> s"Bearer ${accessToken}")

  val scn = scenario("importTSV")
    .feed(tsv("89_WORKSPACE_NAMES_TSV_HERE"))
    .exec(http("tsv_upload_request")
      .post("/service/api/workspaces/broad-dsde-dev/${workspaceName}/importEntities")
      .headers(headers)
      .bodyPart(RawFileBodyPart("entities", "participants.txt").contentType("text/plain"))) //encodes into the multipart/form-data that orchestration wants

  setUp(scn.inject(rampUsers(89) over(60 seconds))).protocols(httpProtocol) //ramp up 89 over 60sec
}