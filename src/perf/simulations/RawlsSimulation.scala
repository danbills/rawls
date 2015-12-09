package default

import io.gatling.core.structure.ScenarioBuilder

import scala.concurrent.duration._
import java.io._

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.jdbc.Predef._

abstract class RawlsSimulation extends Simulation {
  //configs to be config'd
  val lines = scala.io.Source.fromFile("../user-files/config.txt").getLines
  val accessToken = lines.next
  val numUsers = lines.next.toInt
  val testDuration = 30 seconds

  val httpProtocol = http
    .baseURL("https://rawls.dsde-dev.broadinstitute.org")
    .inferHtmlResources()

  val headers = Map("Authorization" -> s"Bearer ${accessToken}",
    "Content-Type" -> "application/json")

  abstract def buildScenario():ScenarioBuilder //subclasses override this

  def run() = {
    setUp(
      buildScenario()
        .inject(rampUsers(numUsers) over testDuration)
    ).protocols(httpProtocol)
  }
  run()
}
