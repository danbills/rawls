package default

import scala.concurrent.duration._

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.jdbc.Predef._

class createWorkspaces100 extends Simulation {

	val accessToken = "ya29.PAKNaoC9r_FSgCqXUDH5f7rb8IfLuDqw9zCAl6YPQfICcuq3hi8LQe2pGl53As953jyS8g" //place your token here :)

	val httpProtocol = http
		.baseURL("https://rawls.dsde-dev.broadinstitute.org")
		.inferHtmlResources()
	//	.extraInfoExtractor(extraInfo => List(extraInfo.response)) //for when we want to extract additional info for the simulation.log

	val headers_0 = Map("Authorization" -> s"Bearer ${accessToken}", 
						"Content-Type" -> "application/json") 

	val scn = scenario("createWorkspaces100")
		.feed(csv("100_workspaces_json.csv")) //a one column csv containing the json bodies to post. generating this per-run would save some time
		.exec(http("request_0")
			.post("/api/workspaces")
			.headers(headers_0)
			.body(StringBody("${workspaceJson}"))) //feeds off of the workspaceJson column in the csv file

	setUp(scn.inject(atOnceUsers(100))).protocols(httpProtocol) //ramp up 100 users over 60 seconds instead of all at once
}