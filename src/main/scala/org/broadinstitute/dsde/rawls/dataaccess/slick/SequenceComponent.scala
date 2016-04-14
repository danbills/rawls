package org.broadinstitute.dsde.rawls.dataaccess.slick

/**
 * Created by mbemis on 4/12/16.
 */

/**
 * Generates 64 bit signed integers for use in batch insert
 */

case class EntityIdRecord(next: Long)
case class AttributeIdRecord(next: Long)

trait SequenceComponent {
  this: DriverComponent =>

  import driver.api._

  class EntityIdTable(tag: Tag) extends Table[EntityIdRecord](tag, "entity_id") {
    //stores the most recently used id
    def next = column[Long]("id", O.PrimaryKey)

    def * = next <> (EntityIdRecord, EntityIdRecord.unapply)
  }

  class AttributeIdTable(tag: Tag) extends Table[AttributeIdRecord](tag, "attribute_id") {
    def next = column[Long]("id", O.PrimaryKey)

    def * = next <> (AttributeIdRecord, AttributeIdRecord.unapply)
  }

  object entityIdQuery extends TableQuery(new EntityIdTable(_)) {

    /*
      Dangerous! this shouldn't be in the final version, rather we should initialize this value to 0 using liquibase
     */
    def put(id: Long): WriteAction[Int] = {
      entityIdQuery += EntityIdRecord(id)
    }

    //returns the next available id in the sequence
    def peek(): ReadAction[EntityIdRecord] = {
      entityIdQuery.result.head
    }

    //returns the next n available ids
    def request(n: Int): ReadWriteAction[Seq[EntityIdRecord]] = {
      peek flatMap { mostRecentId =>
        val newLatestId = mostRecentId.next + n
        entityIdQuery.filter(_.next === mostRecentId.next).map(_.next).update(newLatestId).map { _ =>
          Seq.range(mostRecentId.next, newLatestId).map(EntityIdRecord(_))
        }
      }
    }

  }

  object attributeIdQuery extends TableQuery(new AttributeIdTable(_)) {

    /*
      Dangerous! this shouldn't be in the final version, rather we should initialize this value to 0 using liquibase
     */
    def put(id: Long): WriteAction[Int] = {
      attributeIdQuery += AttributeIdRecord(id)
    }

    //returns the next available id in the sequence
    def peek(): ReadAction[AttributeIdRecord] = {
      attributeIdQuery.result.head
    }

    //returns the next n available ids
    def request(n: Int): ReadWriteAction[Seq[AttributeIdRecord]] = {
      peek flatMap { mostRecentId =>
        val newLatestId = mostRecentId.next + n
        attributeIdQuery.filter(_.next === mostRecentId.next).map(_.next).update(newLatestId).map { _ =>
          Seq.range(mostRecentId.next, newLatestId).map(AttributeIdRecord(_))
        }
      }
    }


  }
}