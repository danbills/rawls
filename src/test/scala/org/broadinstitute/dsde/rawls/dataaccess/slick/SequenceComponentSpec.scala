package org.broadinstitute.dsde.rawls.dataaccess.slick

/**
 * Created by mbemis on 4/14/16.
 */

class SequenceComponentSpec extends TestDriverComponentWithFlatSpecAndMatchers {

  "SequenceComponentSpec" should "modify entity ids" in withEmptyTestDatabase {

    assertResult(EntityIdRecord(0)) {
      runAndWait(entityIdQuery.peek())
    }

    assertResult(Seq(EntityIdRecord(0), EntityIdRecord(1), EntityIdRecord(2))) {
      runAndWait(entityIdQuery.takeMany(3))
    }

    assertResult(EntityIdRecord(3)) {
      runAndWait(entityIdQuery.peek())
    }

    assertResult(EntityIdRecord(3)) {
      runAndWait(entityIdQuery.takeOne)
    }

    assertResult(EntityIdRecord(4)) {
      runAndWait(entityIdQuery.peek())
    }

  }

  it should "modify attribute ids" in withEmptyTestDatabase {

    assertResult(AttributeIdRecord(0)) {
      runAndWait(attributeIdQuery.peek())
    }

    assertResult(Seq(AttributeIdRecord(0), AttributeIdRecord(1), AttributeIdRecord(2))) {
      runAndWait(attributeIdQuery.takeMany(3))
    }

    assertResult(AttributeIdRecord(3)) {
      runAndWait(attributeIdQuery.peek())
    }

    assertResult(AttributeIdRecord(3)) {
      runAndWait(attributeIdQuery.takeOne)
    }

    assertResult(AttributeIdRecord(4)) {
      runAndWait(attributeIdQuery.peek())
    }

  }
}
