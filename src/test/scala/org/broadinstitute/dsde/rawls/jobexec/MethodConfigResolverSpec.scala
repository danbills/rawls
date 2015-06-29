package org.broadinstitute.dsde.rawls.jobexec

import org.broadinstitute.dsde.rawls.dataaccess.{DataSource, RawlsTransaction}
import org.broadinstitute.dsde.rawls.graph.OrientDbTestFixture
import org.broadinstitute.dsde.rawls.model._
import org.joda.time.DateTime
import org.scalatest.{WordSpecLike, Matchers, FlatSpec}

class MethodConfigResolverSpec extends WordSpecLike with Matchers with OrientDbTestFixture {
  val littleWdl =
    """
      |task t1 {
      |  command {
      |    echo ${Int int_arg}
      |  }
      |}
      |
      |workflow w1 {
      |  call t1
      |}
    """.stripMargin

  val intArgName = "w1.t1.int_arg"

  val workspace = new Workspace("workspaces", "test_workspace", DateTime.now, "testUser", Map.empty)

  val sampleGood = new Entity("sampleGood", "Sample", Map("blah" -> AttributeNumber(1)), WorkspaceName("workspaces", "test_workspace"))
  val sampleMissingValue = new Entity("sampleMissingValue", "Sample", Map.empty, WorkspaceName("workspaces", "test_workspace"))

  val configGood = new MethodConfiguration("configGood", "Sample", "method_namespace", "test_method", "1",
    Map.empty, Map(intArgName -> "this.blah"), Map.empty, WorkspaceName("workspaces", "test_workspace"), "config_namespace")

  val configMissingExpr = new MethodConfiguration("configMissingExpr", "Sample", "method_namespace", "test_method", "1",
    Map.empty, Map.empty, Map.empty, WorkspaceName("workspaces", "test_workspace"), "config_namespace")

  class ConfigData extends TestData {
    override def save(txn: RawlsTransaction): Unit = {
      workspaceDAO.save(workspace, txn)
      entityDAO.save("workspaces", "test_workspace", sampleGood, txn)
      entityDAO.save("workspaces", "test_workspace", sampleMissingValue, txn)
      methodConfigDAO.save("workspaces", "test_workspace", configGood, txn)
      methodConfigDAO.save("workspaces", "test_workspace", configMissingExpr, txn)
    }
  }
  val configData = new ConfigData()

  def withConfigData(testCode:DataSource => Any): Unit = {
    withCustomTestDatabase(configData) { dataSource =>
      testCode(dataSource)
    }
  }

  "MethodConfigResolver" should {
    "get validation errors" in withConfigData { dataSource =>
      dataSource.inTransaction { txn =>
        MethodConfigResolver.getValidationErrors(configGood, sampleGood, littleWdl, txn).get(intArgName) should be(None) // Valid input
        MethodConfigResolver.getValidationErrors(configGood, sampleMissingValue, littleWdl, txn).get(intArgName) shouldNot be(None) // Entity missing value
        MethodConfigResolver.getValidationErrors(configMissingExpr, sampleGood, littleWdl, txn).get(intArgName) shouldNot be(None) // Config missing expr
      }
    }
  }
}
