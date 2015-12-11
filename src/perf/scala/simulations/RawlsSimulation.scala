package default

import io.gatling.core.structure.ScenarioBuilder

import scala.concurrent.duration._
import java.io._

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.jdbc.Predef._

import spray.json._

import com.typesafe.config.ConfigFactory

abstract class RawlsSimulation extends Simulation {
  //configs to be config'd
  val lines = scala.io.Source.fromFile("../user-files/config.txt").getLines
  val accessToken = lines.next
  val numUsers = lines.next.toInt
  val testDuration = 30 seconds

  val conf = ConfigFactory.load()
  val bar1 = conf.getInt("foo.bar")

  val httpProtocol = http
    .baseURL("https://rawls.dsde-dev.broadinstitute.org")
    .inferHtmlResources()

  val headers = Map("Authorization" -> s"Bearer ${accessToken}",
    "Content-Type" -> "application/json")

  def buildScenario():ScenarioBuilder //subclasses override this

  def run() = {
    setUp(
      buildScenario()
        .inject(rampUsers(numUsers) over testDuration)
    ).protocols(httpProtocol)
  }
  run()

  //Writes any printlns in op to the given file f.
  //TODO: ditch this entirely in favour of an iterator-based approach
  //see http://gatling.io/docs/2.1.7/session/feeder.html
  def fileGenerator(f: java.io.File)(op: java.io.PrintWriter => Unit) {
    val p = new java.io.PrintWriter(f)
    try {
      op(p)
    } finally {
      p.close()
    }
  }
}
