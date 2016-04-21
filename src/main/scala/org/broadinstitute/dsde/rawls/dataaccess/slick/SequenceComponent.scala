package org.broadinstitute.dsde.rawls.dataaccess.slick

import org.broadinstitute.dsde.rawls.RawlsException

import scala.util.Try

/**
 * Created by mbemis on 4/12/16.
 */

/**
 * Generates integer ids for use in batch insert
 * Number stored is the next available id in the sequence
 */

case class EntityIdRecord(next: Long)
case class AttributeIdRecord(next: Long)

trait SequenceComponent {
  this: DriverComponent =>

  import driver.api._

  class EntityIdTable(tag: Tag) extends Table[EntityIdRecord](tag, "entity_id") {
    //stores the next available id
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

    //returns the next available id in the sequence and increments the counter
    def takeOne(): ReadWriteAction[EntityIdRecord] = {
      peek flatMap { mostRecentId =>
        val newLatestId = mostRecentId.next + 1
        entityIdQuery.filter(_.next === mostRecentId.next).map(_.next).update(newLatestId).map { _ =>
          EntityIdRecord(mostRecentId.next)
        }//.asTry
      }
    }

    //returns the next n available ids and increments the counter to current + n
    def takeMany(n: Int): ReadWriteAction[Seq[EntityIdRecord]] = {
      peek flatMap { mostRecentId =>
        println("Next entity id: " + mostRecentId.next)
        val newLatestId = mostRecentId.next + n
        entityIdQuery.filter(_.next === mostRecentId.next).map(_.next).update(newLatestId).map { _ =>
          Seq.range(mostRecentId.next, newLatestId).map(EntityIdRecord(_))
        }//.asTry
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

    //returns the next available id in the sequence and increments the counter
    //TODO: retry if another query updates the id count before this one can update
    def takeOne(): ReadWriteAction[AttributeIdRecord] = {
      peek flatMap { mostRecentId =>
        val newLatestId = mostRecentId.next + 1
        val result = attributeIdQuery.filter(_.next === mostRecentId.next).map(_.next).update(newLatestId)
        result.flatMap { numRows =>
          numRows match {
            case 1 => result.map { _ =>
              AttributeIdRecord(mostRecentId.next)
            }
            case _ => throw new RawlsException("didn't update any rows")
          }
        }
      }
    }

    //returns the next n available ids and increments the counter to current + n
    def takeMany(n: Int): ReadWriteAction[Seq[AttributeIdRecord]] = {
      peek flatMap { mostRecentId =>
        val newLatestId = mostRecentId.next + n
        val result = attributeIdQuery.filter(_.next === mostRecentId.next).map(_.next).update(newLatestId)
        result.flatMap { numRows =>
          numRows match {
            case 1 => result.map { _ =>
              Seq.range(mostRecentId.next, newLatestId).map(AttributeIdRecord(_))
            }
            case _ => throw new RawlsException("didn't update any rows")
          }
        }
      }
    }

  }

}