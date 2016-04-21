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
      Dangerous! don't use this unless initializing this value in unit tests
     */
    def put(id: Long): WriteAction[Int] = {
      entityIdQuery += EntityIdRecord(id)
    }

    //returns the next available id in the sequence
    def peek(): ReadAction[EntityIdRecord] = {
      entityIdQuery.result.head
    }

    //returns the next available id in the sequence and increments the counter
    //See http://dev.mysql.com/doc/refman/5.7/en/innodb-locking-reads.html for information
    //about the SELECT FOR UPDATE syntax
    def takeOne(): ReadWriteAction[EntityIdRecord] = {
      sql"SELECT id FROM ENTITY_ID FOR UPDATE".as[Long] flatMap { id =>
        sql"UPDATE ENTITY_ID SET id = id + 1".as[Int] map { _ =>
          EntityIdRecord(id.head)
        }
      }
    }

    //returns the next n available ids and increments the counter to current + n
    def takeMany(n: Int): ReadWriteAction[Seq[EntityIdRecord]] = {
      sql"SELECT id FROM ENTITY_ID FOR UPDATE".as[Long] flatMap { id =>
        sql"UPDATE ENTITY_ID SET id = id + $n".as[Int] map { _ =>
          Seq.range(id.head, (id.head + n)).map(EntityIdRecord(_))
        }
      }
    }

  }

  object attributeIdQuery extends TableQuery(new AttributeIdTable(_)) {

    /*
      Dangerous! don't use this unless initializing this value in unit tests
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
      sql"SELECT id FROM ATTRIBUTE_ID FOR UPDATE".as[Long] flatMap { id =>
        sql"UPDATE ATTRIBUTE_ID SET id = id + 1".as[Int] map { _ =>
          AttributeIdRecord(id.head)
        }
      }
    }

    //returns the next n available ids and increments the counter to current + n
    def takeMany(n: Int): ReadWriteAction[Seq[AttributeIdRecord]] = {
      sql"SELECT id FROM ATTRIBUTE_ID FOR UPDATE".as[Long] flatMap { id =>
        sql"UPDATE ATTRIBUTE_ID SET id = id + $n".as[Int] map { _ =>
          Seq.range(id.head, (id.head + n)).map(AttributeIdRecord(_))
        }
      }
    }

  }
}