package org.broadinstitute.dsde.rawls.dataaccess.slick

import org.scalatest.{BeforeAndAfterAll, Matchers, FlatSpec}

class MethodConfigurationComponentSpec extends TestDriverComponent {
  import driver.api._

  val schemas = methodConfigurationQuery.schema ++
    methodConfigurationInputQuery.schema //++
//    methodConfigurationOutputQuery.schema ++
//    methodConfigurationPrereqQuery.schema

  override def beforeAll: Unit = {
    runAndWait(schemas.create)
  }

  "MethodConfigurationComponent" should "create, load, list and delete" in {

    val methodConfig = MethodConfigurationRecord(1, "test", "foo", "1", "sample", "testns", "testname", 1)

    assertResult(Seq()) {
      runAndWait(listMethodConfigurations("1"))
    }

    assertResult(methodConfig) {
      runAndWait(saveMethodConfiguration(methodConfig))
    }

    assertResult(methodConfig) {
      runAndWait(loadMethodConfiguration("test", "foo"))
    }

    assertResult(Seq(methodConfig)) {
      runAndWait(listMethodConfigurations("1"))
    }

    assertResult(1) {
      runAndWait(deleteMethodConfiguration("test", "foo"))
    }

    assertResult(Seq()) {
      runAndWait(listMethodConfigurations("1"))
    }

  }

  it should "list method config inputs, outputs, and prereqs for a method config" in {

    val methodConfig = MethodConfigurationRecord(1, "test", "foo", "1", "sample", "testns", "testname", 1)
    val input = MethodConfigurationInputRecord(1, 1, "test", "input")
    val output = MethodConfigurationOutputRecord(1, 1, "test", "output")
    val prereq = MethodConfigurationPrereqRecord(1, 1, "test", "prereq")

    assertResult(methodConfig) {
      runAndWait(saveMethodConfiguration(methodConfig))
    }

    //inputs
    assertResult(Seq()) {
      runAndWait(listMethodConfigInputs(1))
    }

    assertResult(input) {
      runAndWait(saveMethodConfigInput(input))
    }

    assertResult(Seq(input)) {
      runAndWait(listMethodConfigInputs(1))
    }

    //outputs
    assertResult(Seq()) {
      runAndWait(listMethodConfigOutputs(1))
    }

    assertResult(output) {
      runAndWait(saveMethodConfigOutput(output))
    }

    assertResult(Seq(output)) {
      runAndWait(listMethodConfigOutputs(1))
    }

    //prereqs
    assertResult(Seq()) {
      runAndWait(listMethodConfigPrereqs(1))
    }

    assertResult(prereq) {
      runAndWait(saveMethodConfigPrereq(prereq))
    }

    assertResult(Seq(prereq)) {
      runAndWait(listMethodConfigPrereqs(1))
    }


  }
}
