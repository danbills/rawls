package org.broadinstitute.dsde.rawls.graph

import java.nio.file.Paths

import com.tinkerpop.blueprints.impls.orient.OrientGraph
import org.broadinstitute.dsde.rawls.dataaccess.{FileSystemWorkspaceDAO, WorkspaceDAO}
import org.broadinstitute.dsde.rawls.model._
import org.joda.time.DateTime
import org.scalatest.FunSuite

import scala.util.Random

import WorkspaceGenerator._

class WorkspaceGenerator extends FunSuite {
  test("write") {
    val workspace = generateWorkspace("baz.json")
    val storageDir = Paths.get("/Users/msooknah/Documents/scratch/dsde/big_jsons/workspace_gen")
    val dao = new FileSystemWorkspaceDAO(storageDir)
    dao.save(workspace)

    val loaded = dao.load(workspace.namespace, workspace.name)
    assertResult(workspace) { loaded }
    println("It has " + loaded.entities.get("samples").get.size + " samples")
    println("It has " + loaded.entities.get("analyses").get.size + " analyses")
  }
}

object WorkspaceGenerator {
  val rand = new Random

  val minStrLen = 3
  val maxStrLen = 10

  val minSeqLen = 1
  val maxSeqLen = 10

  val vaultSize = 1000000

  def generateAnalysisType = {
    val analyses = List("BWA", "Picard", "GATK")
    AttributeString(analyses(rand.nextInt(analyses.length)))
  }

  def generateBoolean = AttributeBoolean(rand.nextBoolean())
  def generateNumber = AttributeNumber(rand.nextInt())
  def generateDate = AttributeString(new DateTime(rand.nextLong()).toDate.toString)

  def generateVaultID = AttributeString(rand.nextInt(vaultSize).toString)

  def generateString = {
    val length = minStrLen + rand.nextInt(maxStrLen - minStrLen)
    AttributeString(rand.alphanumeric.take(length).mkString)
  }

  def generateAnyPrimitive = rand.nextInt(3) match {
    case 0 => generateBoolean
    case 1 => generateNumber
    case 2 => generateString
  }

  def generateAnyList = {
    val length = minSeqLen + rand.nextInt(maxSeqLen - minSeqLen)
    val gen = for (i <- 0 to length) yield generateAnyPrimitive
    AttributeList(gen.toSeq)
  }

  def generateAnalysis(name: String) = {
    val attributes = Map(
      "analysisDate" -> generateDate,
      "analysisType" -> generateAnalysisType,
      "parameters" -> generateAnyList
    )
    Entity(name, attributes)
  }

  def generateSample(name: String) = {
    val attributes = Map(
      "vaultID" -> generateVaultID,
      "annotations" -> generateAnyList
      // TODO reference a list of analyses
    )
    Entity(name, attributes)
  }

  def generateSampleSet(name: String) = {
    // TODO
  }

  def generateWorkspace(name: String) = {
    val samples = (for (i <- 0 to 1000) yield "sample"+i).map(s => (s -> generateSample(s)))
    val analyses = (for (i <- 0 to 1000) yield "analysis"+i).map(a => (a -> generateAnalysis(a)))

    Workspace(
      "namespace",
      name,
      DateTime.now().withMillis(0),
      "WorkspaceGenerator",
      Map(
        "samples" -> Map(samples:_*),
        "analyses" -> Map(analyses:_*)
      )
    )
  }

  object GraphGenerator {
    val rand = new Random

    val minSampleSetSize = 4000
    val maxSampleSetSize = 6000

    val minStrLen = 3
    val maxStrLen = 10

    val minSeqLen = 1
    val maxSeqLen = 10

    val vaultSize = 10000

    def generateAnalysisType = {
      val analyses = List("BWA", "Picard", "GATK")
      analyses(rand.nextInt(analyses.length))
    }

    def generateSampleType = {
      if (rand.nextBoolean()) "Tumor" else "Normal"
    }

    def generateBoolean = rand.nextBoolean()
    def generateNumber = rand.nextInt()
    def generateDate = new DateTime(rand.nextLong()).toDate.toString

    def generateVaultID = rand.nextInt(vaultSize).toString

    def generateString = {
      val length = minStrLen + rand.nextInt(maxStrLen - minStrLen)
      rand.alphanumeric.take(length).mkString
    }

    def generateAnyPrimitive = rand.nextInt(3) match {
      case 0 => generateBoolean
      case 1 => generateNumber
      case 2 => generateString
    }

    def generateAnyList = {
      val length = minSeqLen + rand.nextInt(maxSeqLen - minSeqLen)
      val gen = for (i <- 0 to length) yield generateAnyPrimitive
      gen.toSeq
    }

    def addSample(name: String, graph: OrientGraph) = {
      val sample = graph.addVertex("class:Sample", "name", name)
      sample.setProperty("vaultID", generateVaultID)
      sample.setProperty("type", generateSampleType)
      sample.setProperty("annotations", generateAnyList.toString)
      graph.commit()
      sample
    }

    def addSampleSet(name: String, graph: OrientGraph) = {
      val sampleSet = graph.addVertex("class:SampleSet", "name", name)
      sampleSet.setProperty("type", generateString)
      graph.commit()
      val size = minSampleSetSize + rand.nextInt(maxSampleSetSize - minSampleSetSize)
      val samples = for (i <- 0 to size) {
        val sample = addSample(name+"_sample"+i, graph)
        graph.addEdge("class:contains", sampleSet, sample, "contains")
        graph.commit()
      }
      sampleSet
    }

    def addWorkspace(name: String, graph: OrientGraph, numSampleSets: Int) = {
      val workspace = graph.addVertex("class:Workspace", "name", name)
      for (i <- 0 to numSampleSets) {
        val sampleSet = addSampleSet("sampleSet"+i, graph)
        graph.addEdge("class:inWorkspace", workspace, sampleSet, "inWorkspace")
        graph.commit()
      }
      workspace
    }
  }

}