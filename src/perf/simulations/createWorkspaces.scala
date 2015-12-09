package default

import io.gatling.core.structure.ScenarioBuilder

import scala.concurrent.duration._
import java.io._

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.jdbc.Predef._

class createWorkspaces extends RawlsSimulation {
  override def buildScenario(): ScenarioBuilder = {

    def fileGenerator(f: java.io.File)(op: java.io.PrintWriter => Unit) {
      val p = new java.io.PrintWriter(f)
      try {
        op(p)
      } finally {
        p.close()
      }
    }

    val r = scala.util.Random
    val runID = s"gatling_creations_${r.nextInt}"

    //generates a tsv with json bodies to create workspaces
    fileGenerator(new File(s"../user-files/data/createWorkspaces_${runID}.tsv")) { p =>
      p.println("workspaceJson")
      val i = 0
      for (i <- 1 to numUsers) {
        p.println( s""""{""namespace"":""broad-dsde-dev"",""name"":""${runID}_${i}"",""attributes"":{}}"""")
      }
    }

    //generates a list of workspaceNames that are to be created. optionally feed this into deleteWorkspaces.scala to cleanup
    fileGenerator(new File(s"../user-files/data/createWorkspaces_NAMES_${runID}.tsv")) { p =>
      p.println("workspaceName")
      val i = 0
      for (i <- 1 to numUsers) {
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