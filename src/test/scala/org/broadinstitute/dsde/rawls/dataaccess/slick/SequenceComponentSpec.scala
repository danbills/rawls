package org.broadinstitute.dsde.rawls.dataaccess.slick

/**
 * Created by mbemis on 4/14/16.
 */

class SequenceComponentSpec extends TestDriverComponentWithFlatSpecAndMatchers {

  "SequenceComponentSpec" should "peek at the most recently used id" in withEmptyTestDatabase {

    assertResult(EntityIdRecord(0)) {
      runAndWait(entityIdQuery.peek())
    }

    assertResult(Seq(EntityIdRecord(0), EntityIdRecord(1), EntityIdRecord(2))) {
      runAndWait(entityIdQuery.request(3))
    }

    assertResult(EntityIdRecord(3)) {
      runAndWait(entityIdQuery.peek())
    }

    assertResult(Seq(EntityIdRecord(3))) {
      runAndWait(entityIdQuery.request(1))
    }

    assertResult(EntityIdRecord(4)) {
      runAndWait(entityIdQuery.peek())
    }

  }
}
