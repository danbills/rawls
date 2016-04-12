package org.broadinstitute.dsde.rawls.dataaccess.slick

/**
 * Created by mbemis on 4/12/16.
 */

/**
 * Generates 64 bit signed integers for use in batch insert
 */

case class EntityIdRecord(id: Long)
case class AttributeIdRecord(id: Long)

trait IdComponent {
  this: DriverComponent =>

  import driver.api._

  class EntityIdTable(tag: Tag) extends Table[EntityIdRecord](tag, "entity_id") {
    //stores the most recently used id
    def id = column[Long]("id", O.PrimaryKey)

    def * = id <> (EntityIdRecord, EntityIdRecord.unapply)
  }

  class AttributeIdTable(tag: Tag) extends Table[AttributeIdRecord](tag, "attribute_id") {
    def id = column[Long]("id", O.PrimaryKey)

    def * = id <> (AttributeIdRecord, AttributeIdRecord.unapply)
  }

  object entityIdQuery extends TableQuery(new EntityIdTable(_)) {

    def peek(): ReadAction[EntityIdRecord] = {
      entityIdQuery.result.head
    }

//    def request(n: Int): ReadAction[Seq[EntityIdRecord]] = {
//      peek flatMap { mostRecentId =>
//        val latestId = mostRecentId.id + n
//        entityIdQuery.filter(_.id === mostRecentId).map(_.id).update(latestId)
//      }
//    }

  }

  object attributeIdQuery extends TableQuery(new AttributeIdTable(_)) {

    def peek(): ReadAction[AttributeIdRecord] = {
      attributeIdQuery.result.head
    }

//    def request(n: Int): ReadAction[Seq[AttributeIdRecord]] = {
//      peek flatMap { mostRecentId =>
//        val latestId = mostRecentId.id + n
//        attributeIdQuery.filter(_.id === mostRecentId).map(_.id).update(latestId)
//      }
//    }

  }
}