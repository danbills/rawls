package org.broadinstitute.dsde.rawls.dataaccess

import java.util.function.Consumer

import com.thinkaurelius.titan.core.schema.ConsistencyModifier
import com.thinkaurelius.titan.graphdb.database.StandardTitanGraph
import com.thinkaurelius.titan.graphdb.tinkerpop.{TitanBlueprintsTransaction, TitanBlueprintsGraph}
import org.apache.tinkerpop.gremlin.structure.{Vertex, Transaction, Direction, Graph}
import org.scalatest.{Matchers, FlatSpec}

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

import com.thinkaurelius.titan.core.{Cardinality, SchemaViolationException, TitanGraph, TitanFactory}

class TitanSpec extends FlatSpec with Matchers {

  def getTx(graph: Graph): Transaction = {
    val t = graph.tx()
    t.open()
    println("open: " + t)
    t
  }

  def commit(tx: Transaction): Unit = {
    println("commit: " + tx)
    tx.commit()
  }

  "Titan" should "do things" in {
    val factory = TitanFactory.build()
    val tg: Graph = factory
      .set("storage.backend", "embeddedcassandra")
      .set("storage.conf-file", "file:///c:/code/rawls/cassandra.yaml")
      .open

    tg.tx().onReadWrite(Transaction.READ_WRITE_BEHAVIOR.MANUAL)
    tg.tx().onClose(Transaction.CLOSE_BEHAVIOR.MANUAL)




    val mgmt = tg.asInstanceOf[TitanGraph].openManagement()

    val beep = mgmt.makePropertyKey("beep").dataType(classOf[String]).make() //.cardinality(Cardinality.SINGLE)
    val index = mgmt.buildIndex("name",classOf[Vertex]).addKey(beep).buildCompositeIndex()

    //val label = mgmt.makeVertexLabel("Workspace").make()
    mgmt.setConsistency(index, ConsistencyModifier.LOCK)
    mgmt.commit()

    /* NOTE: Titan has vertex/edge labels instead of classes.
    You can either allow them to be made implicitly (on-the-fly), or .set("schema.default", "none")
    to enforce that all labels be pre-defined. I think we need something in between, since we set
    edge labels on the fly and vertex labels upfront; we probably want to make our own DefaultSchemaMaker.
    See http://s3.thinkaurelius.com/docs/titan/1.0.0/schema.html#_automatic_schema_maker
    */

    /* NOTE: It's not possible to retrieve all vertices with a specificed label without a full table scan.
    However, it *is* possible to assign labels to all vertices and then make multi-indices on properties
    constrained to a label, i.e. index on (namespace, name) only for vertices with label Workspace.
    https://groups.google.com/forum/#!topic/aureliusgraphs/ydeht3IQ8dM
    http://s3.thinkaurelius.com/docs/titan/1.0.0/indexes.html#_composite_index
     */

    val tx = getTx(tg)
    val v = tg.addVertex("Workspace") //workspace label
    v.property("name", "myworkspace")
    v.property("namespace", "mynamespace")
    commit(tx)



    val t1 = new Thread(new Runnable {
      def run(): Unit = {
        val txGraph = tg.tx().createThreadedTx[Graph]()
        val myTx = getTx(txGraph)
        Thread.sleep(1000)
        val wsVert = txGraph.traversal().V().hasLabel("Workspace").has("name", "myworkspace").has("namespace", "mynamespace").toList.head
        println("currently: " + wsVert.property("beep"))
        try {
          println("beep boop: " + myTx)
          wsVert.property("beep", "boop")
        } catch {
          case sve: SchemaViolationException => println("LOCKFAIL")
        }

        commit(myTx)
      }
    })

    val t2 = new Thread(new Runnable {
      def run(): Unit = {
        val txGraph = tg.tx().createThreadedTx[Graph]()
        val myTx = getTx(txGraph)
        Thread.sleep(100)
        val wsVert = txGraph.traversal().V().hasLabel("Workspace").has("name", "myworkspace").has("namespace", "mynamespace").toList.head
        println("beep beep: " + myTx)
        wsVert.property("beep", "beep")
        Thread.sleep(100)
        commit(myTx)
      }
    })

    t1.start()
    t2.start()

    t1.join()
    t2.join()

    val tx2 = getTx(tg)
    val wsVert = tg.traversal().V().hasLabel("Workspace").has("name", "myworkspace").has("namespace", "mynamespace").toList.head
    wsVert.properties[Any]() foreach { vtxProp =>
      println(vtxProp.key + ", " + vtxProp.value)
    }
    commit(tx2)
  }
}
