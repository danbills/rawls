package default

import io.gatling.core.structure.ScenarioBuilder

import scala.concurrent.duration._
import java.io._

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.jdbc.Predef._

class createWorkspaces extends RawlsSimulation {

  override def buildScenario(): ScenarioBuilder = {
    val r = scala.util.Random
    val runID = s"gatling_creations_${r.nextInt}"

    //generates a tsv with json bodies to create workspaces
    fileGenerator(new File(s"../user-files/data/createWorkspaces_${runID}.tsv")) { p =>
      p.println("workspaceJson")
      1 to numUsers foreach { i =>
        p.println( s""""{""namespace"":""broad-dsde-dev"",""name"":""${runID}_${i}"",""attributes"":{}}"""")
      }
    }

    //generates a list of workspaceNames that are to be created. optionally feed this into deleteWorkspaces.scala to cleanup
    fileGenerator(new File(s"../user-files/data/createWorkspaces_NAMES_${runID}.tsv")) { p =>
      p.println("workspaceName")
      1 to numUsers foreach { i =>
        p.println(s"${runID}_${i}")
      }
    }

    scenario(s"createWorkspaces_${numUsers}")
      .feed(tsv(s"../user-files/data/createWorkspaces_${runID}.tsv"))
      .exec(http("create_request")
        .post("/api/workspaces")
        .headers(headers)
        .body(StringBody("${workspaceJson}")))
  }
}