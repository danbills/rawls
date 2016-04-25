package org.broadinstitute.dsde.rawls.dataaccess.slick

import java.util.UUID
import javax.xml.bind.DatatypeConverter

import org.broadinstitute.dsde.rawls.{RawlsExceptionWithErrorReport, RawlsException}
import org.broadinstitute.dsde.rawls.dataaccess.SlickWorkspaceContext
import slick.dbio.Effect.Read
import slick.jdbc.GetResult
import org.broadinstitute.dsde.rawls.model._
import slick.jdbc.GetResult
import spray.http.StatusCodes

/**
 * Created by dvoet on 2/4/16.
 */
case class EntityRecord(id: Long, name: String, entityType: String, workspaceId: UUID)
case class EntityAttributeRecord(entityId: Long, attributeId: Long)

trait EntityComponent {
  this: DriverComponent
    with WorkspaceComponent
    with AttributeComponent
    with SequenceComponent =>

  import driver.api._

  class EntityTable(tag: Tag) extends Table[EntityRecord](tag, "ENTITY") {
    def id = column[Long]("id", O.PrimaryKey)
    def name = column[String]("name", O.Length(254))
    def entityType = column[String]("entity_type", O.Length(254))
    def workspaceId = column[UUID]("workspace_id")
    def workspace = foreignKey("FK_ENTITY_WORKSPACE", workspaceId, workspaceQuery)(_.id)
    def uniqueTypeName = index("idx_entity_type_name", (workspaceId, entityType, name), unique = true)
    def * = (id, name, entityType, workspaceId) <> (EntityRecord.tupled, EntityRecord.unapply)
  }

  class EntityAttributeTable(tag: Tag) extends Table[EntityAttributeRecord](tag, "ENTITY_ATTRIBUTE") {
    def entityId = column[Long]("entity_id")
    def attributeId = column[Long]("attribute_id", O.PrimaryKey)

    def entity = foreignKey("FK_ENT_ATTR_ENTITY", entityId, entityQuery)(_.id)
    def attribute = foreignKey("FK_ENT_ATTR_ATTRIBUTE", attributeId, attributeQuery)(_.id, onDelete = ForeignKeyAction.Cascade)

    def * = (entityId, attributeId) <> (EntityAttributeRecord.tupled, EntityAttributeRecord.unapply)
  }

  protected val entityAttributeQuery = TableQuery[EntityAttributeTable]
  
  object entityQuery extends TableQuery(new EntityTable(_)) {
    type EntityQuery = Query[EntityTable, EntityRecord, Seq]
    type EntityQueryWithAttributesAndRefs =  Query[(EntityTable, Rep[Option[(AttributeTable, Rep[Option[EntityTable]])]]), (EntityRecord, Option[(AttributeRecord, Option[EntityRecord])]), Seq]

    implicit val getEntityRecord = GetResult { r => EntityRecord(r.<<, r.<<, r.<<, r.<<) }

    // result structure from entity and attribute list raw sql
    case class EntityListResult(entityRecord: EntityRecord, attributeRecord: Option[AttributeRecord], refEntityRecord: Option[EntityRecord])

    // tells slick how to convert a result row from a raw sql query to an instance of EntityListResult
    implicit val getEntityListResult = GetResult { r =>
      // note that the number and order of all the r.<< match precisely with the select clause of baseEntityAndAttributeSql
      val entityRec = EntityRecord(r.<<, r.<<, r.<<, r.<<)

      val attributeIdOption: Option[Long] = r.<<
      val attributeRecOption = attributeIdOption.map(id => AttributeRecord(id, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<))

      val refEntityRecOption = for {
        attributeRec <- attributeRecOption
        refId <- attributeRec.valueEntityRef
      } yield { EntityRecord(r.<<, r.<<, r.<<, r.<<) }

      EntityListResult(entityRec, attributeRecOption, refEntityRecOption)
    }

    import driver.quoteIdentifier
    // the where clause for this query is filled in specific to the use case
    val baseEntityAndAttributeSql =
      s"""select e.id, e.name, e.entity_type, e.workspace_id, a.id, a.name, a.value_string, a.value_number, a.value_boolean, a.value_entity_ref, a.list_index, e_ref.id, e_ref.name, e_ref.entity_type, e_ref.workspace_id
          from ENTITY e
          left outer join ENTITY_ATTRIBUTE ea on e.id = ea.entity_id
          left outer join ATTRIBUTE a on ea.attribute_id = a.id
          left outer join ENTITY e_ref on a.value_entity_ref = e_ref.id"""

    val baseEntityAndAttributeCountSql =
      s"""select count(distinct e.id), count(1)
          from ENTITY e
          left outer join ENTITY_ATTRIBUTE ea on e.id = ea.entity_id
          left outer join ATTRIBUTE a on ea.attribute_id = a.id
          left outer join ENTITY e_ref on a.value_entity_ref = e_ref.id"""

    val baseAttributesOfEntitiesCountSql =
      s"""select count(1)
          from ENTITY e
          left outer join ENTITY_ATTRIBUTE ea on e.id = ea.entity_id"""

    def entityAttributes(entityId: Long) = for {
      entityAttrRec <- entityAttributeQuery if entityAttrRec.entityId === entityId
      attributeRec <- attributeQuery if entityAttrRec.attributeId === attributeRec.id
    } yield attributeRec

    def findEntityByName(workspaceId: UUID, entityType: String, entityName: String): EntityQuery = {
      filter(entRec => entRec.name === entityName && entRec.entityType === entityType && entRec.workspaceId === workspaceId)
    }

    def findEntityByType(workspaceId: UUID, entityType: String): EntityQuery = {
      filter(entRec => entRec.entityType === entityType && entRec.workspaceId === workspaceId)
    }

    def findEntityByWorkspace(workspaceId: UUID): EntityQuery = {
      filter(_.workspaceId === workspaceId)
    }

    def findEntityById(id: Long): EntityQuery = {
      filter(_.id === id)
    }

    def lookupEntitiesByNames(workspaceId: UUID, entities: Traversable[AttributeEntityReference]): ReadAction[Seq[EntityRecord]] = {
      if (entities.isEmpty) {
        DBIO.successful(Seq.empty)
      } else {
        // slick can't do a query with '(entityType, entityName) in ((?, ?), (?, ?), ...)' so we need raw sql
        val baseSelect = sql"select id, name, entity_type, workspace_id from ENTITY where workspace_id = $workspaceId and (entity_type, name) in ("
        val entityTypeNameTuples = entities.map { case entity => sql"(${entity.entityType}, ${entity.entityName})" }.reduce((a, b) => concatSqlActionsWithDelim(a, b, sql","))
        concatSqlActions(concatSqlActions(baseSelect, entityTypeNameTuples), sql")").as[EntityRecord]
      }
    }

    def lookupEntityAndAttributeCounts(workspaceId: UUID): ReadAction[(Int, Int)] = {
      sql"""#$baseEntityAndAttributeCountSql where e.workspace_id = ${workspaceId}""".as[(Int, Int)].map(_.head)
    }

    def lookupAttributeCountsForEntities(entities: EntityCopyDefinition): ReadAction[Int] = {
      val base = sql"#$baseAttributesOfEntitiesCountSql where e.entity_type = ${entities.entityType} and e.name in ("
      val entityNames = entities.entityNames.map(name => sql"$name").reduce((a, b) => concatSqlActionsWithDelim(a, b, sql","))
      concatSqlActions(concatSqlActions(base, entityNames), sql")").as[Int].map(_.head)
    }

    /** gets the given entity */
    def get(workspaceContext: SlickWorkspaceContext, entityType: String, entityName: String): ReadAction[Option[Entity]] = {
      val sql = sql"""#$baseEntityAndAttributeSql where e.#${quoteIdentifier("name")} = ${entityName} and e.#${quoteIdentifier("entity_type")} = ${entityType} and e.#${quoteIdentifier("workspace_id")} = ${workspaceContext.workspaceId}""".as[EntityListResult]
      unmarshalEntities(sql).map(_.headOption)
    }

    /**
     * converts a query resulting in a number of records representing many entities with many attributes, some of them references
     */
    def unmarshalEntities(entityAndAttributesQuery: EntityQueryWithAttributesAndRefs): ReadAction[Iterable[Entity]] = {
      entityAndAttributesQuery.result map { entityAttributeRecords =>
        val entityRecords = entityAttributeRecords.map(_._1).toSet
        val attributesByEntityId = attributeQuery.unmarshalAttributes(entityAttributeRecords.collect {
          case (entityRec, Some((attributeRec, referenceOption))) => ((entityRec.id, attributeRec), referenceOption)
        })

        entityRecords.map { entityRec =>
          unmarshalEntity(entityRec, attributesByEntityId.getOrElse(entityRec.id, Map.empty))
        }
      }
    }

    def unmarshalEntities(entityAttributeAction: ReadAction[Seq[EntityListResult]]): ReadAction[Iterable[Entity]] = {
      entityAttributeAction.map { entityAttributeRecords =>
        val allEntityRecords = entityAttributeRecords.map(_.entityRecord).toSet

        // note that not all entities have attributes, thus the collect below
        val entitiesWithAttributes = entityAttributeRecords.collect {
          case EntityListResult(entityRec, Some(attributeRec), refEntityRecOption) => ((entityRec.id, attributeRec), refEntityRecOption)
        }

        val attributesByEntityId = attributeQuery.unmarshalAttributes[Long](entitiesWithAttributes)

        allEntityRecords.map { entityRec =>
          unmarshalEntity(entityRec, attributesByEntityId.getOrElse(entityRec.id, Map.empty))
        }
      }
    }

    /** creates or replaces an entity */
    def save(workspaceContext: SlickWorkspaceContext, entity: Entity): ReadWriteAction[Entity] = {
      save(workspaceContext, Seq(entity)).map(_.head)
    }

    def save(workspaceContext: SlickWorkspaceContext, entities: Traversable[Entity]): ReadWriteAction[Traversable[Entity]] = {
      entities.foreach(validateEntity)

      for {
        preExistingEntityRecs <- lookupEntitiesByNames(workspaceContext.workspaceId, entities.map(_.toReference))
        _ <- deleteEntityAttributes(preExistingEntityRecs)
        savingEntityRecs <- insertNewEntities(workspaceContext, entities, preExistingEntityRecs).map(_ ++ preExistingEntityRecs)
        referencedAndSavingEntityRecs <- lookupNotYetLoadedReferences(workspaceContext, entities, savingEntityRecs).map(_ ++ savingEntityRecs)
        _ <- insertAttributes(entities, referencedAndSavingEntityRecs)
      } yield entities
    }

    private def insertAttributes(entities: Traversable[Entity], entityRecs: Traversable[EntityRecord]) = {
      val entityIdsByName = entityRecs.map(r => AttributeEntityReference(r.entityType, r.name) -> r.id).toMap
      val attributeRecsToEntityId = (for {
        entity <- entities
        (attributeName, attribute) <- entity.attributes
        attributeRec <- attributeQuery.marshalAttribute(attributeName, attribute, entityIdsByName)
      } yield attributeRec -> entityIdsByName(entity.toReference)).toMap

      attributeQuery.batchInsertEntityAttributes(attributeRecsToEntityId.keys.toSeq) flatMap { insertedAttributes =>
        val attrRecsWithIds = insertedAttributes.map(attrRec => attrRec -> attributeRecsToEntityId(attrRec.copy(id = 0)))
        batchInsertEntityAttributes(attrRecsWithIds.map { case (attr, entityId) => attr.id -> entityId }.toMap)
      }
    }

    private def lookupNotYetLoadedReferences(workspaceContext: SlickWorkspaceContext, entities: Traversable[Entity], alreadyLoadedEntityRecs: Seq[EntityRecord]): ReadAction[Seq[EntityRecord]] = {
      val notYetLoadedEntityRecs = (for {
        entity <- entities
        (_, attribute) <- entity.attributes
        ref <- attribute match {
          case AttributeEntityReferenceList(l) => l
          case r: AttributeEntityReference => Seq(r)
          case _ => Seq.empty
        }
      } yield ref).toSet -- alreadyLoadedEntityRecs.map(r => AttributeEntityReference(r.entityType, r.name))

      lookupEntitiesByNames(workspaceContext.workspaceId, notYetLoadedEntityRecs) map { foundEntities =>
        if (foundEntities.size != notYetLoadedEntityRecs.size) {
          val notFoundRefs = notYetLoadedEntityRecs -- foundEntities.map(r => AttributeEntityReference(r.entityType, r.name))
          throw new RawlsExceptionWithErrorReport(ErrorReport(StatusCodes.BadRequest, "Could not resolve some entity references", notFoundRefs.map { missingRef =>
            ErrorReport(s"${missingRef.entityType} ${missingRef.entityName} not found", Seq.empty)
          }.toSeq))
        } else {
          foundEntities
        }
      }

    }

    private def insertNewEntities(workspaceContext: SlickWorkspaceContext, entities: Traversable[Entity], preExistingEntityRecs: Seq[EntityRecord]): ReadWriteAction[Seq[EntityRecord]] = {
      val existingEntityTypeNames = preExistingEntityRecs.map(rec => (rec.entityType, rec.name))
      val newEntities = entities.filterNot(e => existingEntityTypeNames.exists(_ ==(e.entityType, e.name)))

      batchInsertEntities(workspaceContext, newEntities.toSeq).map(recsWithEntities => recsWithEntities.keys.toSeq)
    }

    /** deletes an entity */
    def delete(workspaceContext: SlickWorkspaceContext, entityType: String, entityName: String): ReadWriteAction[Boolean] = {
      uniqueResult[EntityRecord](findEntityByName(workspaceContext.workspaceId, entityType, entityName)) flatMap {
        case None => DBIO.successful(false)
        case Some(entityRec) =>
          val deleteActions = deleteEntityAttributes(Seq(entityRec))
          val deleteEntity = findEntityByName(workspaceContext.workspaceId, entityType, entityName).delete
          deleteActions andThen deleteEntity.map(_ > 0)
      }
    }

    /** list all entities of the given type in the workspace */
    def list(workspaceContext: SlickWorkspaceContext, entityType: String): ReadAction[TraversableOnce[Entity]] = {
      val sql = sql"""#$baseEntityAndAttributeSql where e.#${quoteIdentifier("entity_type")} = ${entityType} and e.#${quoteIdentifier("workspace_id")} = ${workspaceContext.workspaceId}""".as[EntityListResult]
      unmarshalEntities(sql)
    }

    def list(workspaceContext: SlickWorkspaceContext, entityRefs: Traversable[AttributeEntityReference]): ReadAction[TraversableOnce[Entity]] = {
      val baseSelect = sql"""#$baseEntityAndAttributeSql where e.#${quoteIdentifier("workspace_id")} = ${workspaceContext.workspaceId} and (e.#${quoteIdentifier("entity_type")}, e.#${quoteIdentifier("name")}) in ("""
      val entityTypeNameTuples = entityRefs.map { case ref => sql"(${ref.entityType}, ${ref.entityName})" }.reduce((a, b) => concatSqlActionsWithDelim(a, b, sql","))
      unmarshalEntities(concatSqlActions(concatSqlActions(baseSelect, entityTypeNameTuples), sql")").as[EntityListResult])
    }

    /**
     * Extends given query to query for attributes (if they exist) and entity references (if they exist).
     * query joinLeft entityAttributeQuery join attributeQuery joinLeft entityQuery
     * @param query
     * @return
     */
    def joinOnAttributesAndRefs(query: EntityQuery): EntityQueryWithAttributesAndRefs = {
      query joinLeft {
        entityAttributeQuery join attributeQuery on (_.attributeId === _.id) joinLeft
          entityQuery on (_._2.valueEntityRef === _.id)
      } on (_.id === _._1._1.entityId) map { result =>
        (result._1, result._2.map { case (a, b) => (a._2, b) })
      }
    }

    def rename(workspaceContext: SlickWorkspaceContext, entityType: String, oldName: String, newName: String): ReadWriteAction[Int] = {
      findEntityByName(workspaceContext.workspaceId, entityType, oldName).map(_.name).update(newName)
    }

    def getEntityTypes(workspaceContext: SlickWorkspaceContext): ReadAction[TraversableOnce[String]] = {
      filter(_.workspaceId === workspaceContext.workspaceId).map(_.entityType).distinct.result
    }

    def getEntityTypesWithCounts(workspaceContext: SlickWorkspaceContext): ReadAction[Map[String, Int]] = {
      filter(_.workspaceId === workspaceContext.workspaceId).groupBy(e => e.entityType).map { case (entityType, entities) =>
        (entityType, entities.countDistinct)
      }.result map { result =>
        result.toMap
      }
    }

    def listEntitiesAllTypes(workspaceContext: SlickWorkspaceContext): ReadAction[TraversableOnce[Entity]] = {
      val sql = sql"""#$baseEntityAndAttributeSql where e.#${quoteIdentifier("workspace_id")} = ${workspaceContext.workspaceId}""".as[EntityListResult]
      unmarshalEntities(sql)
    }

    def cloneAllEntities(entityIds: Seq[EntityIdRecord], attributeIds: Seq[AttributeIdRecord], sourceWorkspaceContext: SlickWorkspaceContext, destWorkspaceContext: SlickWorkspaceContext): ReadWriteAction[Unit] = {
      val allEntitiesAction = listEntitiesAllTypes(sourceWorkspaceContext)

      allEntitiesAction.flatMap(cloneEntities(entityIds, attributeIds, destWorkspaceContext, _))
    }

    def batchInsertEntitiesWithIds(entityIds: Seq[EntityIdRecord], workspaceContext: SlickWorkspaceContext, entities: Seq[Entity]): ReadWriteAction[Map[EntityRecord, Entity]] = {
      val records = entityIds.zipWithIndex.map { case (id, idx) =>
        marshalEntity(id, entities(idx), workspaceContext.workspaceId)
      }

      val recordsGrouped = records.grouped(batchSize).toSeq
      DBIO.sequence(recordsGrouped map { batch =>
        (entityQuery ++= batch)

      }).map(_ => (records zip entities).toMap)
    }

    def batchInsertEntities(workspaceContext: SlickWorkspaceContext, entities: Seq[Entity]): ReadWriteAction[Map[EntityRecord, Entity]] = {
      entityIdQuery.takeMany(entities.size) flatMap { ids =>
        val records = ids.zipWithIndex.map { case (id, idx) =>
          marshalEntity(id, entities(idx), workspaceContext.workspaceId)
        }

        val recordsGrouped = records.grouped(batchSize).toSeq
        DBIO.sequence(recordsGrouped map { batch =>
          (entityQuery ++= batch)

        }).map(_ => (records zip entities).toMap)
      }
    }

    def batchInsertEntityAttributes(entityAttributes: Map[Long, Long]) = {
      val records = entityAttributes.map(rec => EntityAttributeRecord(rec._2, rec._1))

      val recordsGrouped = records.grouped(batchSize).toSeq
      DBIO.sequence(recordsGrouped map { batch =>
        (entityAttributeQuery ++= batch)

      }).map(_ => records)
    }

    def cloneEntities(entityIds: Seq[EntityIdRecord], attributeIds: Seq[AttributeIdRecord], destWorkspaceContext: SlickWorkspaceContext, entities: TraversableOnce[Entity]): ReadWriteAction[Unit] = {
      val inserts = batchInsertEntitiesWithIds(entityIds, destWorkspaceContext, entities.toSeq) flatMap { records =>

        val entityIdsByRef = records.map { case (entityRecord, entity) =>
          AttributeEntityReference(entityRecord.entityType, entityRecord.name) -> entityRecord.id
        }

        val attributeRecordsWithEntityRecords = records.flatMap { case (entityRecord, entity) =>
          entity.attributes.map { case (name, attribute) =>
            attributeQuery.marshalAttribute(name, attribute, entityIdsByRef).map(a => a -> entityRecord)
          }
        }.flatten

        attributeQuery.batchInsertEntityAttributesWithIds(attributeIds, attributeRecordsWithEntityRecords.map(_._1).toSeq) flatMap { attributeRecords =>
          val blah = attributeRecordsWithEntityRecords.map(_._2).zip(attributeRecords)
          batchInsertEntityAttributes(blah.map(foo => foo._2.id -> foo._1.id).toMap)
        }
      }

      inserts.map(_ => Unit)
    }

    /**
     * Starting with entities specified by entityIds, recursively walk down references accumulating all the ids
     * @param entityIds the ids to start with
     * @param accumulatedIds the ids accumulated from the prior call. If you wish entityIds to be in the overall
     *                       results, start with entityIds == accumulatedIds, otherwise start with Seq.empty but note
     *                       that if there is a cycle some of entityIds may be in the result anyway
     * @return the ids of all the entities referred to by entityIds
     */
    private def recursiveGetEntityReferenceIds(entityIds: Set[Long], accumulatedIds: Set[Long]): ReadAction[Set[Long]] = {
      // need to batch because some RDBMSes have a limit on the length of an in clause
      val batchedEntityIds = createBatches(entityIds)

      val batchQueries = batchedEntityIds.map {
        idBatch => filter(_.id inSetBind(idBatch)) join
          entityAttributeQuery on (_.id === _.entityId) join
          attributeQuery on (_._2.attributeId === _.id) filter(_._2.valueEntityRef.isDefined) map (_._2.valueEntityRef)
      }

      val referencesResults = DBIO.sequence(batchQueries.map(_.result))

      referencesResults.map(_.reduce(_ ++ _)).flatMap { refIdOptions =>
        val refIds = refIdOptions.collect { case Some(id) => id }.toSet
        val untraversedIds = refIds -- accumulatedIds
        if (untraversedIds.isEmpty) {
          DBIO.successful(accumulatedIds)
        } else {
          recursiveGetEntityReferenceIds(untraversedIds, accumulatedIds ++ refIds)
        }
      }
    }

    /**
     * used in copyEntities load all the entities to copy
     */
    def getEntitySubtrees(workspaceContext: SlickWorkspaceContext, entityType: String, entityNames: Seq[String]): ReadAction[TraversableOnce[Entity]] = {
      val startingEntityIdsAction = filter(rec => rec.workspaceId === workspaceContext.workspaceId && rec.entityType === entityType && rec.name.inSetBind(entityNames)).map(_.id)
      val entitiesQuery = startingEntityIdsAction.result.flatMap { startingEntityIds =>
        val idSet = startingEntityIds.toSet
        recursiveGetEntityReferenceIds(idSet, idSet)
      } flatMap { ids =>
        DBIO.sequence(ids.map { id =>
          val sql = sql"""#$baseEntityAndAttributeSql where e.#${quoteIdentifier("id")} = ${id}""".as[EntityListResult]
          unmarshalEntities(sql)
        }.toSeq)
      }
      entitiesQuery.map(_.flatten)
    }

    def copyEntities(entityIds: Seq[EntityIdRecord], attributeIds: Seq[AttributeIdRecord], sourceWorkspaceContext: SlickWorkspaceContext, destWorkspaceContext: SlickWorkspaceContext, entityType: String, entityNames: Seq[String]): ReadWriteAction[TraversableOnce[Entity]] = {
      getEntitySubtrees(sourceWorkspaceContext, entityType, entityNames).flatMap { entities =>
        getCopyConflicts(destWorkspaceContext, entities).flatMap { conflicts =>
          if (conflicts.isEmpty) {
            cloneEntities(entityIds, attributeIds, destWorkspaceContext, entities).map(_ => Seq.empty[Entity])
          } else {
            DBIO.successful(conflicts)
          }
        }
      }
    }

    def getCopyConflicts(destWorkspaceContext: SlickWorkspaceContext, entitiesToCopy: TraversableOnce[Entity]): ReadAction[TraversableOnce[Entity]] = {
      val entityQueries = entitiesToCopy.map { entity =>
        findEntityByName(destWorkspaceContext.workspaceId, entity.entityType, entity.name).result.map {
          case Seq() => None
          case _ => Option(entity)
        }
      }
      DBIO.sequence(entityQueries).map(_.toStream.collect { case Some(e) => e })
    }

    def marshalEntity(idRec: EntityIdRecord, entity: Entity, workspaceId: UUID): EntityRecord = {
      EntityRecord(idRec.next, entity.name, entity.entityType, workspaceId)
    }

    def unmarshalEntity(entityRecord: EntityRecord, attributes: Map[String, Attribute]) = {
      Entity(entityRecord.name, entityRecord.entityType, attributes)
    }

    private def insertEntityAttributes(entity: Entity, entityId: Long, workspaceId: UUID): Seq[ReadWriteAction[Int]] = {
      val attributeInserts = entity.attributes.flatMap { case (name, attribute) =>
        attributeQuery.insertAttributeRecords(name, attribute, workspaceId)
      } map (_.flatMap { attributeId =>
        entityAttributeQuery += EntityAttributeRecord(entityId, attributeId)
      })
      attributeInserts.toSeq
    }

//    def deleteEntityAttributes(entityRecords: Seq[EntityRecord]) = {
//      if (entityRecords.isEmpty) {
//        // 0 records deleted
//        DBIO.successful(0)
//      }
//      else {
//        // use plain sql to delete from a join - we were doing a subselect before, which had awful performance, and slick can't do this
//        val deleteBase = sql"""delete a from ATTRIBUTE AS a join ENTITY_ATTRIBUTE AS ea ON a.id = ea.attribute_id where ea.entity_id in ("""
//        val ids = entityRecords.map { case entity => sql"${entity.id}" }.reduce((a, b) => concatSqlActionsWithDelim(a, b, sql","))
//        concatSqlActions(concatSqlActions(deleteBase, ids), sql")").as[Int]
//      }
//    }

    def deleteEntityAttributes(entityRecords: Seq[EntityRecord]) = {
      val entityAttributes = entityAttributeQuery.filter(_.entityId.inSetBind(entityRecords.map(_.id)))
      attributeQuery.filter(_.id in entityAttributes.map(_.attributeId)).delete
    }
  }

  def validateEntity(entity: Entity): Unit = {
    validateUserDefinedString(entity.entityType) // do we need to check this here if we're already validating all edges?
    validateUserDefinedString(entity.name)
    entity.attributes.keys.foreach { value =>
      validateUserDefinedString(value)
      validateAttributeName(value)
    }
  }
}
