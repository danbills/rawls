package default

import scala.concurrent.duration._
import java.io._

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.jdbc.Predef._

class createWorkspaces extends Simulation {

	val lines = scala.io.Source.fromFile("../user-files/config.txt").getLines
	val accessToken = lines.next
	val numUsers = lines.next.toInt

	//function to help us generate TSVs per-run
	def generateTSV(f: java.io.File)(op: java.io.PrintWriter => Unit) {
	  val p = new java.io.PrintWriter(f)
	  try { op(p) } finally { p.close() }
	}

	val httpProtocol = http
		.baseURL("https://rawls.dsde-dev.broadinstitute.org")
		.inferHtmlResources()
	//	.extraInfoExtractor(extraInfo => List(extraInfo.response)) //for when we want to extract additional info for the simulation.log

	val headers = Map("Authorization" -> s"Bearer ${accessToken}",
						"Content-Type" -> "application/json") 

	//generate the TSV to use for this run
	generateTSV(new File("../user-files/data/createWorkspaces100.tsv")) { p =>
		val r = scala.util.Random
		val runID = s"gatling_creations_${r.nextInt(999999999)}"

		p.println("workspaceJson")

		val i = 0
		for(i <- 1 to 100){
			p.println(s""""{""namespace"":""broad-dsde-dev"",""name"":""${runID}_${i}"",""attributes"":{}}"""")
		}
	}

	val scn = scenario(s"createWorkspaces_${numUsers}")
		.feed(tsv("../user-files/data/createWorkspaces100.tsv")) //the tsv from generateTSV
		.exec(http("create_request")
			.post("/api/workspaces")
			.headers(headers)
			.body(StringBody("${workspaceJson}"))) //feeds off of the workspaceJson column in the tsv file

	setUp(scn.inject(rampUsers(numUsers) over(60 seconds))).protocols(httpProtocol) //ramp up n users over 60 seconds
}