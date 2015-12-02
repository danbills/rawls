package default

import scala.concurrent.duration._
import java.io._

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.jdbc.Predef._

class importTSV extends Simulation {

  //specify this stuff in /user-files/config.txt
  val lines = scala.io.Source.fromFile("../user-files/config.txt").getLines
  val accessToken = lines.next
  val numUsers = lines.next.toInt

  val httpProtocol = http
    .baseURL("https://firecloud.dsde-dev.broadinstitute.org") //hit orchestration instead of rawls. this functionality doesn't quite exist in rawls
    .inferHtmlResources()

  val headers = Map("Authorization" -> s"Bearer ${accessToken}")

  val scn = scenario(s"importTSV_${numUsers}")
    .feed(tsv("100_submissions_workspaceNames.tsv"))
    .exec(http("tsv_upload_request")
      .post("/service/api/workspaces/broad-dsde-dev/${workspaceName}/importEntities")
      .headers(headers)
      .bodyPart(RawFileBodyPart("entities", "participants.txt").contentType("text/plain"))) //encodes into the multipart/form-data that orchestration wants

  setUp(scn.inject(rampUsers(numUsers) over(60 seconds))).protocols(httpProtocol) //ramp up n users over 60sec
}