package org.broadinstitute.dsde.rawls.graph

import com.orientechnologies.orient.core.intent.OIntentMassiveInsert
import com.tinkerpop.blueprints.impls.orient.OrientGraph
import com.tinkerpop.blueprints.{Direction, Edge, Vertex}
import com.tinkerpop.gremlin.java.GremlinPipeline
import org.broadinstitute.dsde.rawls.Timer._
import org.broadinstitute.dsde.rawls.graph.WorkspaceGenerator.GraphGenerator
import org.scalatest.{BeforeAndAfter, FunSuite}

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

class OrientDbGraphTest extends FunSuite with BeforeAndAfter {

  implicit class OrientGiraffe(graph: OrientGraph) {
    // default vertex construtor
    def addVertex() = graph.addVertex(null, List.empty[Object].asJava)
  }

  implicit class Vortex(vertex: Vertex) {
    def printProps() = {
      println("Vertex: " + vertex.getId.toString)
      vertex.getPropertyKeys.map(k => " " + k + ": " + vertex.getProperty(k)).foreach(println)
    }
  }

  implicit class AwesomeEdge(edge: Edge) {
    def printProps() = {
      println("Edge: " + edge.getId.toString)
      edge.getPropertyKeys.map(k => " " + k + ": " + edge.getProperty(k)).foreach(println)
    }
  }

  var graph: OrientGraph = _

  before {
    //graph = new OrientGraph("memory:testdb")
    //graph = new OrientGraph("remote:localhost/testdb")
    val dbName = "test_"+(System.currentTimeMillis())
    // DB needs to be in certain directory for UI to see it
    // TODO: put this in test config or something
    graph = new OrientGraph("plocal:/usr/local/orientdb/orientdb-community-2.0.8/databases/"+dbName)
    println("----------------Local DB running at "+dbName+"----------------")
  }

  after {
    graph.shutdown
  }

  ignore("basic") {
    // commit changes with commit(). roll back with rollback().

    // add some vertices
    val alice = graph.addVertex()
    alice.setProperty("name", "Alice")
    val bob = graph.addVertex()
    bob.setProperty("name", "Bob")
    val charles = graph.addVertex()
    charles.setProperty("name", "Charles")
    graph.commit()

    // add edges
    val aliceKnowsBob = graph.addEdge(null, alice, bob, "knows")
    val bobKnowsCharles = graph.addEdge(null, bob, charles, "knows")
    val charlesLikesAlice = graph.addEdge(null, charles, alice, "likes")
    val charlesLikesBob = graph.addEdge(null, charles, bob, "likes")
    graph.commit()

    // add some properties
    alice.setProperties(Map("age" -> 20, "city" -> "Boston").asJava)
    graph.commit()

    // print props for a given vertex
    graph.getVertices("name", "Alice").head.printProps()

    // print single prop for all vertices
    graph.getVertices.foreach(v => println(v.getProperty("name")))

    // traverse all connections in graph (using * syntax)
    graph.traverse().target(alice).field("*").execute().foreach(println)

    // traverse specific things
    graph.traverse().target(charles).fields("in_knows", "out_knows").execute().foreach(println)
    graph.traverse().target(charles).fields("out_likes").execute().foreach(println)
    graph.getVertices("name", "Charles").head.getEdges(Direction.OUT, "likes").foreach(_.setProperty("foo",true))
    graph.getEdges.foreach(_.printProps())
  }

  ignore("import_from_model") {
    val workspace = WorkspaceGenerator.generateWorkspace("MyWorkspace")

    // put vertices in
    workspace.entities.foreach(f => {
      // f._1 is entity type, f._2 is map of instances of that type
      f._2.foreach(g => {
        val foo = graph.addVertex("class:" + f._1, Map("name" -> g._1).asJava)
        //foo.setProperties(g._2.attributes.asJava) // this doesn't work. it also erases all existing fields.
        g._2.attributes.foreach(h => {
          foo.setProperty(h._1, h._2.toString)
        })
      })
    })
  }

  ignore("big_workspace") {
    timeIt("Create big workspace") {
      graph.declareIntent(new OIntentMassiveInsert)
      GraphGenerator.addWorkspace("BigWorkspace", graph, 100)
      graph.declareIntent(null)
      graph.commit()
    }

    timeIt("Return the whole workspace") {
      graph.getRawGraph // what is this doing?
    }

    runGeneralTests(graph)
  }

  ignore("many_workspaces") {
    timeIt("Create many workspaces") {
      graph.declareIntent(new OIntentMassiveInsert)
      for (i <- 0 to 20) GraphGenerator.addWorkspace("SmallWorkspace"+i, graph, 5)
      graph.declareIntent(null)
      graph.commit()
    }

    timeIt("Get workspace names") {
      val workspaceNames = graph.getVerticesOfClass("Workspace").map(_.getProperty[String]("name"))
    }

    timeIt("Get all workspaces containing specific sample") {
      val vaultID = GraphGenerator.generateVaultID
      // TODO can we avoid using toList, so that everything happens server-side?
      // TODO I tried using the Gremlin filter via a PipeFunction, but it didn't work...seems to be due to Scala/Java differences
      val workspaces = graph.getVerticesOfClass("Workspace").filter(w =>
        new GremlinPipeline[Vertex, Vertex].start(w).out("inWorkspace").out("contains").toList.filter(_.getProperty("vaultID") == vaultID).size > 0
      )
      println(workspaces.size + " workspaces have sample with Vault ID " + vaultID)
    }

    runGeneralTests(graph)
  }

  def runGeneralTests(graph: OrientGraph) = {
    timeIt("Get all tumor samples") {
      val tumors = graph.getVertices("Sample.type", "Tumor")
      println("Saw " + tumors.size +" tumor samples")
    }

    timeIt("Get all samples in a sample set, without Gremlin") {
      val sampleSet = graph.getVerticesOfClass("SampleSet").head
      val edges = sampleSet.getEdges(Direction.OUT, "contains")
      val samples = edges.map(e => e.getVertex(Direction.OUT))
      println(sampleSet.getProperty[String]("name") + " has " + samples.size + " samples")
    }

    timeIt("Get all samples in a sample set, with Gremlin") {
      val sampleSet = graph.getVerticesOfClass("SampleSet").head
      val pipeline: GremlinPipeline[Vertex, Vertex] = new GremlinPipeline
      val samples = pipeline.start(sampleSet).out("contains").toList
      println(sampleSet.getProperty[String]("name") + " has " + samples.size + " samples")
    }

    timeIt("Update, insert, remove some fields") {
      val sample = graph.getVertices("Sample.type", "Tumor").head
      sample.setProperty("type", "Normal")
      graph.commit()
      sample.setProperty("blacklisted", true)
      graph.commit()
      sample.removeProperty("blacklisted")
      graph.commit()
      sample.printProps()
    }

    timeIt("Add a sample to an existing sample set") {
      val sampleSet = graph.getVerticesOfClass("SampleSet").head
      val sample = GraphGenerator.addSample("shinyNewSample", graph)
      graph.addEdge("class:contains", sampleSet, sample, "contains")
      graph.commit()
    }

    timeIt("Remove a sample set, while retaining samples") {
      // TODO in the current implementation, the workspace root node only points to sample sets, but not individual samples,
      // TODO so this will leave a bunch of disconnected nodes
      val sampleSet = graph.getVerticesOfClass("SampleSet").head
      sampleSet.remove()
      graph.commit()
      println("Removed " + sampleSet.getProperty("name"))
      println("Head is now at " + graph.getVerticesOfClass("SampleSet").head.getProperty("name"))
    }

    timeIt("Remove a sample set with all its samples") {
      val sampleSet = graph.getVerticesOfClass("SampleSet").head
      val samples = new GremlinPipeline[Vertex, Vertex].start(sampleSet).out("contains").toList
      sampleSet.remove()
      samples.foreach(_.remove)
      graph.commit()
      println("Removed " + sampleSet.getProperty("name") + " and its " + samples.size + " samples")
    }
  }
}
