package org.broadinstitute.dsde.rawls.workspace

import java.io.File
import java.util.UUID

import akka.actor.{ActorRef, Actor, Props}
import com.typesafe.config.ConfigFactory
import org.broadinstitute.dsde.rawls.RawlsException
import org.broadinstitute.dsde.rawls.dataaccess._
import org.broadinstitute.dsde.rawls.jobexec.MethodConfigResolver
import org.broadinstitute.dsde.rawls.jobexec.SubmissionSupervisor.SubmissionStarted
import org.broadinstitute.dsde.rawls.model.WorkspaceAccessLevel.WorkspaceAccessLevel
import org.broadinstitute.dsde.rawls.model.WorkspaceACLJsonSupport.{WorkspaceAccessLevelFormat, WorkspaceACLFormat, WorkspaceACLUpdateFormat}
import org.broadinstitute.dsde.rawls.model.WorkspaceJsonSupport._
import org.broadinstitute.dsde.rawls.model.ExecutionJsonSupport.SubmissionFormat
import org.broadinstitute.dsde.rawls.model.ExecutionJsonSupport.SubmissionValidationReportFormat
import org.broadinstitute.dsde.rawls.model.ExecutionJsonSupport.WorkflowOutputsFormat
import org.broadinstitute.dsde.rawls.model.ExecutionJsonSupport.ExecutionServiceValidationFormat
import org.broadinstitute.dsde.rawls.dataaccess.{MethodConfigurationDAO, EntityDAO, WorkspaceDAO}
import org.broadinstitute.dsde.rawls.model._
import org.broadinstitute.dsde.rawls.model.AttributeConversions
import org.broadinstitute.dsde.rawls.expressions._
import org.broadinstitute.dsde.rawls.webservice.PerRequest
import org.broadinstitute.dsde.rawls.webservice.PerRequest.{RequestCompleteWithLocation, RequestCompleteWithHeaders, PerRequestMessage, RequestComplete}
import AttributeUpdateOperations._
import org.broadinstitute.dsde.rawls.workspace.WorkspaceService._
import org.joda.time.DateTime
import spray.http
import spray.http.Uri
import spray.http.HttpHeaders.Location
import spray.http.{StatusCodes, HttpCookie, HttpHeaders}
import spray.httpx.SprayJsonSupport._
import spray.httpx.UnsuccessfulResponseException
import spray.json._
import spray.routing.RequestContext

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * Created by dvoet on 4/27/15.
 */

object WorkspaceService {
  sealed trait WorkspaceServiceMessage
  case class RegisterUser(callbackPath: String) extends WorkspaceServiceMessage
  case class CompleteUserRegistration( authCode: String, state: String, callbackPath: String) extends WorkspaceServiceMessage

  case class CreateWorkspace(workspace: WorkspaceRequest) extends WorkspaceServiceMessage
  case class GetWorkspace(workspaceName: WorkspaceName) extends WorkspaceServiceMessage
  case class DeleteWorkspace(workspaceName: WorkspaceName) extends WorkspaceServiceMessage
  case class UpdateWorkspace(workspaceName: WorkspaceName, operations: Seq[AttributeUpdateOperation]) extends WorkspaceServiceMessage
  case object ListWorkspaces extends WorkspaceServiceMessage
  case class CloneWorkspace(sourceWorkspace: WorkspaceName, destWorkspace: WorkspaceName) extends WorkspaceServiceMessage
  case class GetACL(workspaceName: WorkspaceName) extends WorkspaceServiceMessage
  case class UpdateACL(workspaceName: WorkspaceName, aclUpdates: Seq[WorkspaceACLUpdate]) extends WorkspaceServiceMessage

  case class CreateEntity(workspaceName: WorkspaceName, entity: Entity) extends WorkspaceServiceMessage
  case class GetEntity(workspaceName: WorkspaceName, entityType: String, entityName: String) extends WorkspaceServiceMessage
  case class UpdateEntity(workspaceName: WorkspaceName, entityType: String, entityName: String, operations: Seq[AttributeUpdateOperation]) extends WorkspaceServiceMessage
  case class DeleteEntity(workspaceName: WorkspaceName, entityType: String, entityName: String) extends WorkspaceServiceMessage
  case class RenameEntity(workspaceName: WorkspaceName, entityType: String, entityName: String, newName: String) extends WorkspaceServiceMessage
  case class EvaluateExpression(workspaceName: WorkspaceName, entityType: String, entityName: String, expression: String) extends WorkspaceServiceMessage
  case class ListEntityTypes(workspaceName: WorkspaceName) extends WorkspaceServiceMessage
  case class ListEntities(workspaceName: WorkspaceName, entityType: String) extends WorkspaceServiceMessage
  case class CopyEntities(entityCopyDefinition: EntityCopyDefinition, uri:Uri) extends WorkspaceServiceMessage
  case class BatchUpsertEntities(workspaceName: WorkspaceName, entityUpdates: Seq[EntityUpdateDefinition]) extends WorkspaceServiceMessage
  case class BatchUpdateEntities(workspaceName: WorkspaceName, entityUpdates: Seq[EntityUpdateDefinition]) extends WorkspaceServiceMessage

  case class CreateMethodConfiguration(workspaceName: WorkspaceName, methodConfiguration: MethodConfiguration) extends WorkspaceServiceMessage
  case class GetMethodConfiguration(workspaceName: WorkspaceName, methodConfigurationNamespace: String, methodConfigurationName: String) extends WorkspaceServiceMessage
  case class UpdateMethodConfiguration(workspaceName: WorkspaceName, methodConfiguration: MethodConfiguration) extends WorkspaceServiceMessage
  case class DeleteMethodConfiguration(workspaceName: WorkspaceName, methodConfigurationNamespace: String, methodConfigurationName: String) extends WorkspaceServiceMessage
  case class RenameMethodConfiguration(workspaceName: WorkspaceName, methodConfigurationNamespace: String, methodConfigurationName: String, newName: String) extends WorkspaceServiceMessage
  case class CopyMethodConfiguration(methodConfigNamePair: MethodConfigurationNamePair) extends WorkspaceServiceMessage
  case class CopyMethodConfigurationFromMethodRepo(query: MethodRepoConfigurationQuery) extends WorkspaceServiceMessage
  case class ListMethodConfigurations(workspaceName: WorkspaceName) extends WorkspaceServiceMessage
  case class CreateMethodConfigurationTemplate( methodRepoMethod: MethodRepoMethod ) extends WorkspaceServiceMessage

  case class ListSubmissions(workspaceName: WorkspaceName) extends WorkspaceServiceMessage
  case class CreateSubmission(workspaceName: WorkspaceName, submission: SubmissionRequest) extends WorkspaceServiceMessage
  case class ValidateSubmission(workspaceName: WorkspaceName, submission: SubmissionRequest) extends WorkspaceServiceMessage
  case class GetSubmissionStatus(workspaceName: WorkspaceName, submissionId: String) extends WorkspaceServiceMessage
  case class AbortSubmission(workspaceName: WorkspaceName, submissionId: String) extends WorkspaceServiceMessage
  case class GetWorkflowOutputs(workspaceName: WorkspaceName, submissionId: String, workflowId: String) extends WorkspaceServiceMessage

  def props(workspaceServiceConstructor: UserInfo => WorkspaceService, userInfo: UserInfo): Props = {
    Props(workspaceServiceConstructor(userInfo))
  }

  def constructor(dataSource: DataSource, containerDAO: GraphContainerDAO, methodRepoDAO: MethodRepoDAO, executionServiceDAO: ExecutionServiceDAO, gcsDAO: GoogleCloudStorageDAO, submissionSupervisor : ActorRef)(userInfo: UserInfo) =
    new WorkspaceService(userInfo, dataSource, containerDAO, methodRepoDAO, executionServiceDAO, gcsDAO, submissionSupervisor)
}

class WorkspaceService(userInfo: UserInfo, dataSource: DataSource, containerDAO: GraphContainerDAO, methodRepoDAO: MethodRepoDAO, executionServiceDAO: ExecutionServiceDAO, gcsDAO: GoogleCloudStorageDAO, submissionSupervisor : ActorRef) extends Actor {

  override def receive = {
    case RegisterUser(callbackPath) => context.parent ! registerUser(callbackPath)
    case CompleteUserRegistration(authCode, state, callbackPath) => context.parent ! completeUserRegistration(authCode,state,callbackPath)

    case CreateWorkspace(workspace) => context.parent ! createWorkspace(workspace)
    case GetWorkspace(workspaceName) => context.parent ! getWorkspace(workspaceName)
    case DeleteWorkspace(workspaceName) => context.parent ! deleteWorkspace(workspaceName)
    case UpdateWorkspace(workspaceName, operations) => context.parent ! updateWorkspace(workspaceName, operations)
    case ListWorkspaces => context.parent ! listWorkspaces(dataSource)
    case CloneWorkspace(sourceWorkspace, destWorkspace) => context.parent ! cloneWorkspace(sourceWorkspace, destWorkspace)
    case GetACL(workspaceName) => context.parent ! getACL(workspaceName)
    case UpdateACL(workspaceName, aclUpdates) => context.parent ! updateACL(workspaceName, aclUpdates)

    case CreateEntity(workspaceName, entity) => context.parent ! createEntity(workspaceName, entity)
    case GetEntity(workspaceName, entityType, entityName) => context.parent ! getEntity(workspaceName, entityType, entityName)
    case UpdateEntity(workspaceName, entityType, entityName, operations) => context.parent ! updateEntity(workspaceName, entityType, entityName, operations)
    case DeleteEntity(workspaceName, entityType, entityName) => context.parent ! deleteEntity(workspaceName, entityType, entityName)
    case RenameEntity(workspaceName, entityType, entityName, newName) => context.parent ! renameEntity(workspaceName, entityType, entityName, newName)
    case EvaluateExpression(workspaceName, entityType, entityName, expression) => context.parent ! evaluateExpression(workspaceName, entityType, entityName, expression)
    case ListEntityTypes(workspaceName) => context.parent ! listEntityTypes(workspaceName)
    case ListEntities(workspaceName, entityType) => context.parent ! listEntities(workspaceName, entityType)
    case CopyEntities(entityCopyDefinition, uri: Uri) => context.parent ! copyEntities(entityCopyDefinition, uri)
    case BatchUpsertEntities(workspaceName, entityUpdates) => context.parent ! batchUpsertEntities(workspaceName, entityUpdates)
    case BatchUpdateEntities(workspaceName, entityUpdates) => context.parent ! batchUpdateEntities(workspaceName, entityUpdates)

    case CreateMethodConfiguration(workspaceName, methodConfiguration) => context.parent ! createMethodConfiguration(workspaceName, methodConfiguration)
    case RenameMethodConfiguration(workspaceName, methodConfigurationNamespace, methodConfigurationName, newName) => context.parent ! renameMethodConfiguration(workspaceName, methodConfigurationNamespace, methodConfigurationName, newName)
    case DeleteMethodConfiguration(workspaceName, methodConfigurationNamespace, methodConfigurationName) => context.parent ! deleteMethodConfiguration(workspaceName, methodConfigurationNamespace, methodConfigurationName)
    case GetMethodConfiguration(workspaceName, methodConfigurationNamespace, methodConfigurationName) => context.parent ! getMethodConfiguration(workspaceName, methodConfigurationNamespace, methodConfigurationName)
    case UpdateMethodConfiguration(workspaceName, methodConfiguration) => context.parent ! updateMethodConfiguration(workspaceName, methodConfiguration)
    case CopyMethodConfiguration(methodConfigNamePair) => context.parent ! copyMethodConfiguration(methodConfigNamePair)
    case CopyMethodConfigurationFromMethodRepo(query) => context.parent ! copyMethodConfigurationFromMethodRepo(query)
    case ListMethodConfigurations(workspaceName) => context.parent ! listMethodConfigurations(workspaceName)
    case CreateMethodConfigurationTemplate( methodRepoMethod: MethodRepoMethod ) => context.parent ! createMethodConfigurationTemplate(methodRepoMethod)

    case ListSubmissions(workspaceName) => context.parent ! listSubmissions(workspaceName)
    case CreateSubmission(workspaceName, submission) => context.parent ! createSubmission(workspaceName, submission)
    case ValidateSubmission(workspaceName, submission) => context.parent ! validateSubmission(workspaceName, submission)
    case GetSubmissionStatus(workspaceName, submissionId) => context.parent ! getSubmissionStatus(workspaceName, submissionId)
    case AbortSubmission(workspaceName, submissionId) => context.parent ! abortSubmission(workspaceName, submissionId)
    case GetWorkflowOutputs(workspaceName, submissionId, workflowId) => context.parent ! workflowOutputs(workspaceName, submissionId, workflowId)
  }

  def registerUser(callbackPath: String): PerRequestMessage = {
    RequestCompleteWithHeaders(StatusCodes.SeeOther,Location(gcsDAO.getGoogleRedirectURI(userInfo.userId, callbackPath)))
  }

  def completeUserRegistration(authCode: String, state: String, callbackPath: String): PerRequestMessage = {
    gcsDAO.storeUser(userInfo.userId,authCode,state,callbackPath)
    RequestComplete(StatusCodes.Created)
  }

  private def createBucketName(workspaceName: String) = s"${workspaceName}-${UUID.randomUUID}"

  def createWorkspace(workspaceRequest: WorkspaceRequest): PerRequestMessage =
    dataSource inTransaction { txn =>
      containerDAO.workspaceDAO.load(workspaceRequest.toWorkspaceName, txn) match {
        case Some(_) => PerRequest.RequestComplete(StatusCodes.Conflict, s"Workspace ${workspaceRequest.namespace}/${workspaceRequest.name} already exists")
        case None =>
          val bucketName = createBucketName(workspaceRequest.name)
          Try( gcsDAO.createBucket(userInfo.userId, workspaceRequest.namespace, bucketName) ) match {
            case Failure(err) =>
              RequestComplete(StatusCodes.Forbidden,s"Unable to create bucket for ${workspaceRequest.namespace}/${workspaceRequest.name}: "+err.getMessage)
            case Success(_) =>
              Try( setupWorkspaceGroupACLs(WorkspaceName(workspaceRequest.namespace, workspaceRequest.name), bucketName) ) match {
                case Failure(err) =>
                  gcsDAO.deleteBucket(userInfo.userId, workspaceRequest.namespace, bucketName)
                  RequestComplete(StatusCodes.Forbidden, s"Unable to create groups for ${workspaceRequest.namespace}/${workspaceRequest.name}: "+err.getMessage)
                case Success(_) =>
                  val workspace = Workspace(workspaceRequest.namespace, workspaceRequest.name,bucketName,DateTime.now,userInfo.userId,workspaceRequest.attributes)
                  containerDAO.workspaceDAO.save(workspace, txn)
                  RequestCompleteWithLocation((StatusCodes.Created,workspace), workspace.toWorkspaceName.path)
              }
          }
      }
    }

  def setupWorkspaceGroupACLs(workspaceName: WorkspaceName, bucketName: String): Unit =
    dataSource inTransaction { txn =>
      gcsDAO.setupACL(userInfo.userId, bucketName, workspaceName)
    }

  def getWorkspace(workspaceName: WorkspaceName): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspace(workspaceName, txn) { workspace =>
        requireAccess(workspaceName, workspace.bucketName, WorkspaceAccessLevel.Read, txn) {
          RequestComplete(workspace)
        }
      }
    }

  def deleteWorkspace(workspaceName: WorkspaceName): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspaceContext(workspaceName, txn) { workspaceContext =>
        requireAccess(workspaceName, workspaceContext.bucketName, WorkspaceAccessLevel.Owner, txn) {
          import scala.concurrent.ExecutionContext.Implicits.global
          //Attempt to abort any running workflows so they don't write any more to the bucket.
          //Notice that we're kicking off Futures to do the aborts concurrently, but we never collect their results!
          //This is because there's nothing we can do if Cromwell fails, so we might as well move on and let the
          //ExecutionContext run the futures whenever
          containerDAO.submissionDAO.list(workspaceContext, txn).flatMap(_.workflows).toList collect {
            case wf if !wf.status.isDone => Future { executionServiceDAO.abort(wf.workflowId, userInfo.authCookie) }
          }

          gcsDAO.deleteBucket(userInfo.userId,workspaceContext.workspaceName.namespace,workspaceContext.bucketName)
          gcsDAO.teardownACL(userInfo.userId,workspaceContext.bucketName, workspaceContext.workspaceName)
          containerDAO.workspaceDAO.delete(workspaceContext.workspaceName, txn)

          RequestComplete(StatusCodes.Accepted, s"Your Google bucket ${workspaceContext.bucketName} will be deleted within 24h.")
        }
      }
    }

  def updateWorkspace(workspaceName: WorkspaceName, operations: Seq[AttributeUpdateOperation]): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspace(workspaceName, txn) { workspace =>
        requireAccess(workspaceName, workspace.bucketName, WorkspaceAccessLevel.Write, txn) {
          try {
            val updatedWorkspace = applyOperationsToWorkspace(workspace, operations)
            RequestComplete(containerDAO.workspaceDAO.save(updatedWorkspace, txn))
          } catch {
            case e: AttributeUpdateOperationException => RequestComplete(http.StatusCodes.BadRequest, s"in ${workspaceName}, ${e.getMessage}")
          }
        }
      }
    }

  def listWorkspaces(dataSource: DataSource): PerRequestMessage =
    dataSource inTransaction { txn =>
      val response = for (
        (workspaceName, accessLevel) <- gcsDAO.getWorkspaces(userInfo.userId);
        workspaceContext <- containerDAO.workspaceDAO.loadContext(workspaceName, txn)
      ) yield {
        WorkspaceListResponse(accessLevel,
          containerDAO.workspaceDAO.loadFromContext(workspaceContext),
          getWorkspaceSubmissionStats(workspaceContext, txn),
          gcsDAO.getOwners(workspaceName)
        )
      }

      RequestComplete(response)
    }

  private def getWorkspaceSubmissionStats(workspaceContext: WorkspaceContext, txn: RawlsTransaction): WorkspaceSubmissionStats = {
    val submissions = containerDAO.submissionDAO.list(workspaceContext, txn)

    val workflowsOrderedByDateDesc = submissions.flatMap(_.workflows).toVector.sortWith { (first, second) =>
      first.statusLastChangedDate.isAfter(second.statusLastChangedDate)
    }

    WorkspaceSubmissionStats(
      lastSuccessDate = workflowsOrderedByDateDesc.find(_.status == WorkflowStatuses.Succeeded).map(_.statusLastChangedDate),
      lastFailureDate = workflowsOrderedByDateDesc.find(_.status == WorkflowStatuses.Failed).map(_.statusLastChangedDate),
      runningSubmissionsCount = submissions.count(_.status == SubmissionStatuses.Submitted)
    )
  }

  def cloneWorkspace(sourceWorkspaceName: WorkspaceName, destWorkspaceName: WorkspaceName): PerRequestMessage =
    dataSource inTransaction { txn =>
      (containerDAO.workspaceDAO.load(sourceWorkspaceName, txn), containerDAO.workspaceDAO.load(destWorkspaceName, txn)) match {
        case (Some(sourceWorkspace), None) => {
          val bucketName = createBucketName(destWorkspaceName.name)
          Try( gcsDAO.createBucket(userInfo.userId, destWorkspaceName.namespace, bucketName) ) match {
            case Failure(err) => RequestComplete(StatusCodes.Forbidden,s"Unable to create bucket for ${destWorkspaceName}: "+err.getMessage)
            case Success(_) =>
              Try( setupWorkspaceGroupACLs(destWorkspaceName, bucketName) ) match {
                case Failure(err) =>
                  gcsDAO.deleteBucket(userInfo.userId, destWorkspaceName.namespace, bucketName)
                  RequestComplete(StatusCodes.Forbidden, s"Unable to create groups for ${destWorkspaceName}: " + err.getMessage)
                case Success(_) =>
                  val destWorkspace = containerDAO.workspaceDAO.save(Workspace(destWorkspaceName.namespace, destWorkspaceName.name, bucketName, DateTime.now, userInfo.userId, sourceWorkspace.attributes), txn)
                  // now get the contexts. just call .get because it should be impossible to get None at this point
                  val sourceWorkspaceContext = containerDAO.workspaceDAO.loadContext(sourceWorkspaceName, txn).get
                  val destWorkspaceContext = containerDAO.workspaceDAO.loadContext(destWorkspaceName, txn).get
                  containerDAO.entityDAO.cloneAllEntities(sourceWorkspaceContext, destWorkspaceContext, txn)
                  // TODO add a method for cloning all method configs, instead of doing this
                  containerDAO.methodConfigurationDAO.list(sourceWorkspaceContext, txn).foreach { methodConfig =>
                    containerDAO.methodConfigurationDAO.save(destWorkspaceContext,
                      containerDAO.methodConfigurationDAO.get(sourceWorkspaceContext, methodConfig.namespace, methodConfig.name, txn).get, txn)
                  }
                  RequestCompleteWithLocation((StatusCodes.Created, destWorkspace), destWorkspace.toWorkspaceName.path)
              }
          }
        }
        case (None, _) => RequestComplete(StatusCodes.NotFound, s"Source workspace ${sourceWorkspaceName} not found")
        case (_, Some(_)) => RequestComplete(StatusCodes.Conflict, s"Destination workspace ${destWorkspaceName} already exists")
      }
    }

  def getACL(workspaceName: WorkspaceName): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Owner, txn) { workspaceContext =>
        Try(gcsDAO.getACL(workspaceContext.bucketName, workspaceName)) match {
          case Success(acl) => RequestComplete(StatusCodes.OK, acl)
          case Failure(err) => RequestComplete(StatusCodes.Forbidden, s"Can't retrieve ACL for workspace ${workspaceName}: " + err.getMessage())
        }
      }
    }

  def updateACL(workspaceName: WorkspaceName, aclUpdates: Seq[WorkspaceACLUpdate]): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Owner, txn) { workspaceContext =>
        val updateErrors = gcsDAO.updateACL(workspaceContext.bucketName, workspaceName, aclUpdates)
        updateErrors.size match {
          case 0 => RequestComplete(StatusCodes.OK)
          case _ => RequestComplete(StatusCodes.Conflict, updateErrors)
        }
      }
    }

  def copyEntities(entityCopyDef: EntityCopyDefinition, uri: Uri): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(entityCopyDef.sourceWorkspace, WorkspaceAccessLevel.Read, txn) { sourceWorkspaceContext =>
        withWorkspaceContextAndPermissions(entityCopyDef.destinationWorkspace, WorkspaceAccessLevel.Write, txn) { destWorkspaceContext =>
          val entityNames = entityCopyDef.entityNames
          val entityType = entityCopyDef.entityType
          val conflicts = containerDAO.entityDAO.copyEntities(sourceWorkspaceContext, destWorkspaceContext, entityType, entityNames, txn)
          conflicts.size match {
            case 0 => {
              // get the entities that were copied into the destination workspace
              val entityCopies = containerDAO.entityDAO.list(destWorkspaceContext, entityType, txn).filter((e: Entity) => entityNames.contains(e.name)).toList
              RequestComplete(StatusCodes.Created, entityCopies)
            }
            case _ => {
              val basePath = s"/${destWorkspaceContext.workspaceName.namespace}/${destWorkspaceContext.workspaceName.name}/entities/"
              val conflictUris = conflicts.map(conflict => uri.copy(path = Uri.Path(basePath + s"${conflict.entityType}/${conflict.name}")).toString())
              val conflictingEntities = ConflictingEntities(conflictUris.toSeq)
              RequestComplete(StatusCodes.Conflict, conflictingEntities)
            }
          }
        }
      }
    }

  def createEntity(workspaceName: WorkspaceName, entity: Entity): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Write, txn) { workspaceContext =>
        containerDAO.entityDAO.get(workspaceContext, entity.entityType, entity.name, txn) match {
          case Some(_) => RequestComplete(StatusCodes.Conflict, s"${entity.entityType} ${entity.name} already exists in ${workspaceName}")
          case None => RequestCompleteWithLocation((StatusCodes.Created, containerDAO.entityDAO.save(workspaceContext, entity, txn)), entity.path(workspaceContext.workspaceName))
        }
      }
    }

  def batchUpdateEntities(workspaceName: WorkspaceName, entityUpdates: Seq[EntityUpdateDefinition]): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Write, txn) { workspaceContext =>
        val results = entityUpdates.map { entityUpdate =>
          val entity = containerDAO.entityDAO.get(workspaceContext, entityUpdate.entityType, entityUpdate.name, txn)
          entity match {
            case Some(e) =>
              val trial = Try {
                val updatedEntity = applyOperationsToEntity(e, entityUpdate.operations)
                containerDAO.entityDAO.save(workspaceContext, updatedEntity, txn)
              }
              (entityUpdate, trial)
            case None => (entityUpdate, Failure(new RuntimeException("Entity does not exist")))
          }
        }
        val errorMessages = results.collect{
          case (entityUpdate, Failure(regrets)) => s"Could not update ${entityUpdate.entityType} ${entityUpdate.name} : ${regrets.getMessage}"
        }
        if(errorMessages.isEmpty) {
          RequestComplete(StatusCodes.NoContent)
        } else {
          RequestComplete(StatusCodes.BadRequest, errorMessages)
        }
      }
    }

  def batchUpsertEntities(workspaceName: WorkspaceName, entityUpdates: Seq[EntityUpdateDefinition]): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Write, txn) { workspaceContext =>
        val results = entityUpdates.map { entityUpdate =>
          val entity = containerDAO.entityDAO.get(workspaceContext, entityUpdate.entityType, entityUpdate.name, txn) match {
            case Some(e) => e
            case None => containerDAO.entityDAO.save(workspaceContext, Entity(entityUpdate.name, entityUpdate.entityType, Map.empty), txn)
          }
          val trial = Try {
            val updatedEntity = applyOperationsToEntity(entity, entityUpdate.operations)
            containerDAO.entityDAO.save(workspaceContext, updatedEntity, txn)
          }
          (entityUpdate, trial)
        }
        val errorMessages = results.collect {
          case (entityUpdate, Failure(regrets)) => s"Could not update ${entityUpdate.entityType} ${entityUpdate.name} : ${regrets.getMessage}"
        }
        if (errorMessages.isEmpty) {
          RequestComplete(StatusCodes.NoContent)
        } else {
          RequestComplete(StatusCodes.BadRequest, errorMessages)
        }
      }
    }

  def listEntityTypes(workspaceName: WorkspaceName): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Read, txn) { workspaceContext =>
        RequestComplete(containerDAO.entityDAO.getEntityTypes(workspaceContext, txn).toSeq)
      }
    }

  def listEntities(workspaceName: WorkspaceName, entityType: String): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Read, txn) { workspaceContext =>
        RequestComplete(containerDAO.entityDAO.list(workspaceContext, entityType, txn).toList)
      }
    }

  def getEntity(workspaceName: WorkspaceName, entityType: String, entityName: String): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Read, txn) { workspaceContext =>
        withEntity(workspaceContext, entityType, entityName, txn) { entity =>
          PerRequest.RequestComplete(entity)
        }
      }
    }

  def updateEntity(workspaceName: WorkspaceName, entityType: String, entityName: String, operations: Seq[AttributeUpdateOperation]): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Write, txn) { workspaceContext =>
        withEntity(workspaceContext, entityType, entityName, txn) { entity =>
          try {
            val updatedEntity = applyOperationsToEntity(entity, operations)
            RequestComplete(containerDAO.entityDAO.save(workspaceContext, updatedEntity, txn))
          } catch {
            case e: AttributeUpdateOperationException => RequestComplete(http.StatusCodes.BadRequest, s"in ${workspaceName}, ${e.getMessage}")
          }
        }
      }
    }

  def deleteEntity(workspaceName: WorkspaceName, entityType: String, entityName: String): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Write, txn) { workspaceContext =>
        withEntity(workspaceContext, entityType, entityName, txn) { entity =>
          containerDAO.entityDAO.delete(workspaceContext, entity.entityType, entity.name, txn)
          RequestComplete(http.StatusCodes.NoContent)
        }
      }
    }

  def renameEntity(workspaceName: WorkspaceName, entityType: String, entityName: String, newName: String): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Write, txn) { workspaceContext =>
        withEntity(workspaceContext, entityType, entityName, txn) { entity =>
          containerDAO.entityDAO.get(workspaceContext, entity.entityType, newName, txn) match {
            case None =>
              containerDAO.entityDAO.rename(workspaceContext, entity.entityType, entity.name, newName, txn)
              RequestComplete(http.StatusCodes.NoContent)
            case Some(_) => RequestComplete(StatusCodes.Conflict, s"Destination ${entity.entityType} ${newName} already exists")
          }
        }
      }
    }

  def evaluateExpression(workspaceName: WorkspaceName, entityType: String, entityName: String, expression: String): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Read, txn) { workspaceContext =>
        txn withGraph { graph =>
          new ExpressionEvaluator(new ExpressionParser())
            .evalFinalAttribute(workspaceContext, entityType, entityName, expression) match {
            case Success(result) => RequestComplete(http.StatusCodes.OK, result)
            case Failure(regret) => RequestComplete(http.StatusCodes.BadRequest, regret.getMessage)
          }
        }
      }
    }

  /**
   * Applies the sequence of operations in order to the entity.
   *
   * @param entity to update
   * @param operations sequence of operations
   * @throws AttributeNotFoundException when removing from a list attribute that does not exist
   * @throws AttributeUpdateOperationException when adding or removing from an attribute that is not a list
   * @return the updated entity
   */
  def applyOperationsToEntity(entity: Entity, operations: Seq[AttributeUpdateOperation]): Entity = {
    entity.copy(attributes = applyAttributeUpdateOperations(entity, operations))
  }

  /**
   * Applies the sequence of operations in order to the workspace.
   *
   * @param workspace to update
   * @param operations sequence of operations
   * @throws AttributeNotFoundException when removing from a list attribute that does not exist
   * @throws AttributeUpdateOperationException when adding or removing from an attribute that is not a list
   * @return the updated entity
   */
  def applyOperationsToWorkspace(workspace: Workspace, operations: Seq[AttributeUpdateOperation]): Workspace = {
    workspace.copy(attributes = applyAttributeUpdateOperations(workspace, operations))
  }

  private def applyAttributeUpdateOperations(attributable: Attributable, operations: Seq[AttributeUpdateOperation]): Map[String, Attribute] = {
    operations.foldLeft(attributable.attributes) { (startingAttributes, operation) =>
      operation match {
        case AddUpdateAttribute(attributeName, attribute) => startingAttributes + (attributeName -> attribute)

        case RemoveAttribute(attributeName) => startingAttributes - attributeName

        case AddListMember(attributeListName, newMember) =>
          startingAttributes.get(attributeListName) match {
            case Some(AttributeEmptyList) =>
              newMember match {
                case AttributeNull =>
                  startingAttributes
                case newMember: AttributeValue =>
                  startingAttributes + (attributeListName -> AttributeValueList(Seq(newMember)))
                case newMember: AttributeEntityReference =>
                  startingAttributes + (attributeListName -> AttributeEntityReferenceList(Seq(newMember)))
                case _ => throw new AttributeUpdateOperationException("Cannot create list with that type.")
              }

            case Some(l: AttributeValueList) =>
              newMember match {
                case AttributeNull =>
                  startingAttributes
                case newMember: AttributeValue =>
                  startingAttributes + (attributeListName -> AttributeValueList(l.list :+ newMember))
                case _ => throw new AttributeUpdateOperationException("Cannot add non-value to list of values.")
              }

            case Some(l: AttributeEntityReferenceList) =>
              newMember match {
                case AttributeNull =>
                  startingAttributes
                case newMember: AttributeEntityReference =>
                  startingAttributes + (attributeListName -> AttributeEntityReferenceList(l.list :+ newMember))
                case _ => throw new AttributeUpdateOperationException("Cannot add non-reference to list of references.")
              }

            case None =>
              newMember match {
                case AttributeNull =>
                  startingAttributes + (attributeListName -> AttributeEmptyList)
                case newMember: AttributeValue =>
                  startingAttributes + (attributeListName -> AttributeValueList(Seq(newMember)))
                case newMember: AttributeEntityReference =>
                  startingAttributes + (attributeListName -> AttributeEntityReferenceList(Seq(newMember)))
                case _ => throw new AttributeUpdateOperationException("Cannot create list with that type.")
              }

            case Some(_) => throw new AttributeUpdateOperationException(s"$attributeListName of ${attributable.briefName} is not a list")
          }

        case RemoveListMember(attributeListName, removeMember) =>
          startingAttributes.get(attributeListName) match {
            case Some(l: AttributeValueList) =>
              startingAttributes + (attributeListName -> AttributeValueList(l.list.filterNot(_ == removeMember)))
            case Some(l: AttributeEntityReferenceList) =>
              startingAttributes + (attributeListName -> AttributeEntityReferenceList(l.list.filterNot(_ == removeMember)))
            case None => throw new AttributeNotFoundException(s"$attributeListName of ${attributable.briefName} does not exist")
            case Some(_) => throw new AttributeUpdateOperationException(s"$attributeListName of ${attributable.briefName} is not a list")
          }
      }
    }
  }

  def createMethodConfiguration(workspaceName: WorkspaceName, methodConfiguration: MethodConfiguration): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Write, txn) { workspaceContext =>
        containerDAO.methodConfigurationDAO.get(workspaceContext, methodConfiguration.namespace, methodConfiguration.name, txn) match {
          case Some(_) => RequestComplete(StatusCodes.Conflict, s"${methodConfiguration.name} already exists in ${workspaceName}")
          case None => RequestCompleteWithLocation((StatusCodes.Created, containerDAO.methodConfigurationDAO.save(workspaceContext, methodConfiguration, txn)), methodConfiguration.path(workspaceContext.workspaceName))
        }
      }
    }

  def deleteMethodConfiguration(workspaceName: WorkspaceName, methodConfigurationNamespace: String, methodConfigurationName: String): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Write, txn) { workspaceContext =>
        withMethodConfig(workspaceContext, methodConfigurationNamespace, methodConfigurationName, txn) { methodConfig =>
          containerDAO.methodConfigurationDAO.delete(workspaceContext, methodConfigurationNamespace, methodConfigurationName, txn)
          RequestComplete(http.StatusCodes.NoContent)
        }
      }
    }

  def renameMethodConfiguration(workspaceName: WorkspaceName, methodConfigurationNamespace: String, methodConfigurationName: String, newName: String): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Write, txn) { workspaceContext =>
        withMethodConfig(workspaceContext, methodConfigurationNamespace, methodConfigurationName, txn) { methodConfiguration =>
          containerDAO.methodConfigurationDAO.get(workspaceContext, methodConfigurationNamespace, newName, txn) match {
            case None =>
              containerDAO.methodConfigurationDAO.rename(workspaceContext, methodConfigurationNamespace, methodConfigurationName, newName, txn)
              RequestComplete(http.StatusCodes.NoContent)
            case Some(_) => RequestComplete(StatusCodes.Conflict, s"Destination ${newName} already exists")
          }
        }
      }
    }

  def updateMethodConfiguration(workspaceName: WorkspaceName, methodConfiguration: MethodConfiguration): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Write, txn) { workspaceContext =>
        containerDAO.methodConfigurationDAO.get(workspaceContext, methodConfiguration.namespace, methodConfiguration.name, txn) match {
          case Some(_) =>
            RequestComplete(containerDAO.methodConfigurationDAO.save(workspaceContext, methodConfiguration, txn))
          case None => RequestComplete(StatusCodes.NotFound)
        }
      }
    }

  def getMethodConfiguration(workspaceName: WorkspaceName, methodConfigurationNamespace: String, methodConfigurationName: String): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Read, txn) { workspaceContext =>
        withMethodConfig(workspaceContext, methodConfigurationNamespace, methodConfigurationName, txn) { methodConfig =>
          PerRequest.RequestComplete(methodConfig)
        }
      }
    }

  def copyMethodConfiguration(mcnp: MethodConfigurationNamePair): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(mcnp.source.workspaceName, WorkspaceAccessLevel.Read, txn) { workspaceContext =>
        containerDAO.methodConfigurationDAO.get(workspaceContext, mcnp.source.namespace, mcnp.source.name, txn) match {
          case None => RequestComplete(StatusCodes.NotFound)
          case Some(methodConfig) => saveCopiedMethodConfiguration(methodConfig, mcnp.destination, txn)
        }
      }
    }

  def copyMethodConfigurationFromMethodRepo(methodRepoQuery: MethodRepoConfigurationQuery): PerRequestMessage =
    dataSource inTransaction { txn =>
      methodRepoDAO.getMethodConfig(methodRepoQuery.methodRepoNamespace, methodRepoQuery.methodRepoName, methodRepoQuery.methodRepoSnapshotId, userInfo.authCookie) match {
        case None => RequestComplete(StatusCodes.NotFound)
        case Some(entity) =>
          try {
            // if JSON parsing fails, catch below
            val methodConfig = entity.payload.map(JsonParser(_).convertTo[MethodConfiguration])
            methodConfig match {
              case Some(targetMethodConfig) => saveCopiedMethodConfiguration(targetMethodConfig, methodRepoQuery.destination, txn)
              case None => RequestComplete(StatusCodes.UnprocessableEntity, "Method Repo missing configuration payload")
            }
          }
          catch { case e: Exception =>
            val message = "Error parsing Method Repo response: " + e.getMessage
            RequestComplete(StatusCodes.UnprocessableEntity, message)
          }
      }
    }

  private def saveCopiedMethodConfiguration(methodConfig: MethodConfiguration, dest: MethodConfigurationName, txn: RawlsTransaction) =
    withWorkspaceContextAndPermissions(dest.workspaceName, WorkspaceAccessLevel.Write, txn) { workspaceContext =>
      containerDAO.methodConfigurationDAO.get(workspaceContext, dest.namespace, dest.name, txn) match {
        case Some(existingMethodConfig) => RequestComplete(StatusCodes.Conflict, existingMethodConfig)
        case None =>
          val target = methodConfig.copy(name = dest.name, namespace = dest.namespace)
          val targetMethodConfig = containerDAO.methodConfigurationDAO.save(workspaceContext, target, txn)
          RequestCompleteWithLocation((StatusCodes.Created, targetMethodConfig), targetMethodConfig.path(workspaceContext.workspaceName))
      }
    }

  def listMethodConfigurations(workspaceName: WorkspaceName): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Read, txn) { workspaceContext =>
        // use toList below to eagerly iterate through the response from methodConfigurationDAO.list
        // to ensure it is evaluated within the transaction
        RequestComplete(containerDAO.methodConfigurationDAO.list(workspaceContext, txn).toList)
      }
    }

  def createMethodConfigurationTemplate( methodRepoMethod: MethodRepoMethod ): PerRequestMessage = {
    val method = methodRepoDAO.getMethod(methodRepoMethod.methodNamespace,methodRepoMethod.methodName,methodRepoMethod.methodVersion,userInfo.authCookie)
    if ( method.isEmpty ) RequestComplete(StatusCodes.NotFound,methodRepoMethod)
    else if ( method.get.payload.isEmpty ) RequestComplete(StatusCodes.BadRequest,"Empty payload.")
    else RequestComplete(MethodConfigResolver.toMethodConfiguration(method.get.payload.get,methodRepoMethod))
  }

  /**
   * This is the function that would get called if we had a validate method config endpoint.
   */
  def validateMethodConfig(workspaceName: WorkspaceName,
    methodConfigurationNamespace: String, methodConfigurationName: String,
    entityType: String, entityName: String, authCookie: HttpCookie): PerRequestMessage = {
      dataSource inTransaction { txn =>
        withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Read, txn) { workspaceContext =>
          withMethodConfig(workspaceContext, methodConfigurationNamespace, methodConfigurationName, txn) { methodConfig =>
            withEntity(workspaceContext, entityType, entityName, txn) { entity =>
              withMethod(workspaceContext, methodConfig.methodRepoMethod.methodNamespace, methodConfig.methodRepoMethod.methodName, methodConfig.methodRepoMethod.methodVersion, authCookie) { method =>
                withWdl(method) { wdl =>
                  MethodConfigResolver.resolveInputsOrGatherErrors(workspaceContext, methodConfig, entity, wdl) match {
                    case Left(failures) => RequestComplete(StatusCodes.OK, failures)
                    case Right(unpacked) =>
                      val idation = executionServiceDAO.validateWorkflow(wdl, MethodConfigResolver.propertiesToWdlInputs(unpacked), authCookie)
                      RequestComplete(StatusCodes.OK, idation)
                  }
                }
              }
            }
          }
        }
      }
    }

  def listSubmissions(workspaceName: WorkspaceName): PerRequestMessage =
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Read, txn) { workspaceContext =>
        RequestComplete(containerDAO.submissionDAO.list(workspaceContext, txn).toList)
      }
    }

  def createSubmission(workspaceName: WorkspaceName, submissionRequest: SubmissionRequest): PerRequestMessage =
    withSubmissionParameters(workspaceName,submissionRequest) {
      (txn: RawlsTransaction, workspaceContext: WorkspaceContext, methodConfig: MethodConfiguration, agoraEntity: AgoraEntity, wdl: String, jobEntities: Seq[Entity]) =>
        //Attempt to resolve method inputs and submit the workflows to Cromwell, and build the submission status accordingly.
        val submittedWorkflows = jobEntities.map(e => submitWorkflow(workspaceContext, methodConfig, e, wdl, userInfo.authCookie, txn))
        val newSubmission = Submission(submissionId = UUID.randomUUID().toString,
          submissionDate = DateTime.now(),
          submitter = userInfo.userId,
          methodConfigurationNamespace = methodConfig.namespace,
          methodConfigurationName = methodConfig.name,
          submissionEntity = AttributeEntityReference(entityType = submissionRequest.entityType, entityName = submissionRequest.entityName),
          workflows = submittedWorkflows collect { case Right(e) => e },
          notstarted = submittedWorkflows collect { case Left(e) => e },
          status = if (submittedWorkflows.forall(_.isLeft)) SubmissionStatuses.Done else SubmissionStatuses.Submitted)

        if (newSubmission.status == SubmissionStatuses.Submitted) {
          submissionSupervisor ! SubmissionStarted(workspaceName, newSubmission, userInfo.authCookie)
        }

        containerDAO.submissionDAO.save(workspaceContext, newSubmission, txn)
        RequestComplete(StatusCodes.Created, newSubmission)
      }

  def validateSubmission(workspaceName: WorkspaceName, submissionRequest: SubmissionRequest): PerRequestMessage =
    withSubmissionParameters(workspaceName,submissionRequest) {
      (txn: RawlsTransaction, workspaceContext: WorkspaceContext, methodConfig: MethodConfiguration, agoraEntity: AgoraEntity, wdl: String, jobEntities: Seq[Entity]) =>
        val resolvedInputs = jobEntities map{ entity => entity -> MethodConfigResolver.resolveInputs(workspaceContext,methodConfig,entity,wdl) }
        val (goodEntMap, badEntMap) = resolvedInputs partition{ case (entity,inputMap) => inputMap.values.forall(_.isSuccess) }
        val methodConfigInputs = methodConfig.inputs.toSeq.map{ case (wdlName,attr) => SubmissionValidationInput(wdlName,attr.value) }
        val header = SubmissionValidationHeader(methodConfig.rootEntityType,methodConfigInputs)
        val goodEnts = goodEntMap map{ case (entity,inputMap) =>
          SubmissionValidationEntityInputs(entity.name,inputMap.values.map{ foo => SubmissionValidationValue(foo.toOption,None)}.toSeq)}
        val badEnts = badEntMap map{ case (entity,inputMap) =>
          SubmissionValidationEntityInputs(entity.name,inputMap.values.map{
            case Failure(regrets) => SubmissionValidationValue(None,Option(regrets.getMessage))
            case Success(attr) => SubmissionValidationValue(Option(attr),None)
          }.toSeq)
        }
        RequestComplete(StatusCodes.OK,SubmissionValidationReport(header,goodEnts,badEnts))
    }

  def getSubmissionStatus(workspaceName: WorkspaceName, submissionId: String) = {
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Read, txn) { workspaceContext =>
        withSubmission(workspaceContext, submissionId, txn) { submission =>
          RequestComplete(submission)
        }
      }
    }
  }

  def abortSubmission(workspaceName: WorkspaceName, submissionId: String) = {
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Write, txn) { workspaceContext =>
          withSubmission(workspaceContext, submissionId, txn) { submission =>
            val aborts = submission.workflows.map( wf =>
              Try(executionServiceDAO.abort(wf.workflowId, userInfo.authCookie)) match {
                case Success(_) => Success(wf.workflowId)
                //NOTE: Cromwell returns 403 Forbidden if you try to abort a workflow that's already in a terminal
                //status. This is fine for our purposes, so we turn it into a Success.
                case Failure(ure:UnsuccessfulResponseException) if ure.response.status == StatusCodes.Forbidden => Success(wf.workflowId)
                case Failure(regret) => Failure(regret)
              }
            )

            if (aborts.count(_.isFailure) == 0) {
              RequestComplete(StatusCodes.NoContent)
            } else {
              //Not entirely sure what to do with bad responses; am aggregating them under a 500 for now.
              //Possible responses:
              //400 - malformed workflow ID (how'd we end up with that in our DB?)
              //404 - unknown workflow ID (uh oh)
              //500 - cromwell ISE
              RequestComplete(StatusCodes.InternalServerError, aborts.collect({case Failure(regret) => regret.getMessage}).toJson.toString )
            }
          }
        }
      }
    }

  /**
   * Munges together the output of Cromwell's /outputs and /logs endpoints, grouping them by task name */
  private def mergeWorkflowOutputs(execOuts: ExecutionServiceOutputs, execLogs: ExecutionServiceLogs, workflowId: String): PerRequestMessage = {
    val outs = execOuts.outputs
    val logs = execLogs.logs

    //Cromwell workflow outputs look like workflow_name.task_name.output_name.
    //Under perverse conditions it might just be workflow_name.output_name.
    //Group outputs by everything left of the rightmost dot.
    val outsByTask = outs groupBy { case (k,_) => k.split('.').dropRight(1).mkString(".") }

    val taskMap = (outsByTask.keySet ++ logs.keySet).map( key => key -> TaskOutput( logs.get(key), outsByTask.get(key)) ).toMap
    RequestComplete(StatusCodes.OK, WorkflowOutputs(workflowId, taskMap))
  }

  /**
   * Get the list of outputs for a given workflow in this submission */
  def workflowOutputs(workspaceName: WorkspaceName, submissionId: String, workflowId: String) = {
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Read, txn) { workspaceContext =>
        withSubmission(workspaceContext, submissionId, txn) { submission =>
          withWorkflow(workspaceContext.workspaceName, submission, workflowId) { workflow =>

            val mergedOutputs:Try[PerRequestMessage] = for {
              outsRq <- Try(executionServiceDAO.outputs(workflowId, userInfo.authCookie))
              logsRq <- Try(executionServiceDAO.logs(workflowId, userInfo.authCookie))
            } yield mergeWorkflowOutputs(outsRq, logsRq, workflowId)

            mergedOutputs match {
              case Success(happyResponse) => happyResponse
              case Failure(ure:UnsuccessfulResponseException) => RequestComplete(ure.response.status, ure.response.message.toString)
              case Failure(regret) => RequestComplete(StatusCodes.InternalServerError, regret.getMessage)
            }
          }
        }
      }
    }
  }


  // helper methods

  private def noSuchWorkspaceMessage(workspaceName: WorkspaceName) = s"${workspaceName} does not exist"
  private def accessDeniedMessage(workspaceName: WorkspaceName) = s"insufficient permissions to perform operation on ${workspaceName}"

  private def withWorkspaceContextAndPermissions(workspaceName: WorkspaceName, accessLevel: WorkspaceAccessLevel, txn: RawlsTransaction)(op: (WorkspaceContext) => PerRequestMessage): PerRequestMessage = {
    withWorkspaceContext(workspaceName, txn) { workspaceContext =>
      requireAccess(workspaceName, workspaceContext.bucketName, accessLevel, txn) { op(workspaceContext) }
    }
  }

  private def withWorkspaceContext(workspaceName: WorkspaceName, txn: RawlsTransaction)(op: (WorkspaceContext) => PerRequestMessage) = {
    containerDAO.workspaceDAO.loadContext(workspaceName, txn) match {
      case None => RequestComplete(http.StatusCodes.NotFound, noSuchWorkspaceMessage(workspaceName))
      case Some(workspaceContext) => op(workspaceContext)
    }
  }

  private def requireAccess(workspaceName: WorkspaceName, bucketName: String, requiredLevel: WorkspaceAccessLevel, txn: RawlsTransaction)(codeBlock: => PerRequestMessage): PerRequestMessage = {
    val userLevel = gcsDAO.getMaximumAccessLevel(userInfo.userId, workspaceName)
    if (userLevel >= requiredLevel) codeBlock
    else if (userLevel >= WorkspaceAccessLevel.Read) RequestComplete(http.StatusCodes.Forbidden, accessDeniedMessage(workspaceName))
    else RequestComplete(http.StatusCodes.NotFound, noSuchWorkspaceMessage(workspaceName))
  }

  private def withWorkspace(workspaceName: WorkspaceName, txn: RawlsTransaction)(op: (Workspace) => PerRequestMessage) = {
    containerDAO.workspaceDAO.load(workspaceName, txn) match {
      case None => RequestComplete(http.StatusCodes.NotFound, noSuchWorkspaceMessage(workspaceName))
      case Some(workspace) => op(workspace)
    }
  }

  private def withEntity(workspaceContext: WorkspaceContext, entityType: String, entityName: String, txn: RawlsTransaction)(op: (Entity) => PerRequestMessage): PerRequestMessage = {
    containerDAO.entityDAO.get(workspaceContext, entityType, entityName, txn) match {
      case None => RequestComplete(http.StatusCodes.NotFound, s"${entityType} ${entityName} does not exist in ${workspaceContext}")
      case Some(entity) => op(entity)
    }
  }

  private def withMethodConfig(workspaceContext: WorkspaceContext, methodConfigurationNamespace: String, methodConfigurationName: String, txn: RawlsTransaction)(op: (MethodConfiguration) => PerRequestMessage): PerRequestMessage = {
    containerDAO.methodConfigurationDAO.get(workspaceContext, methodConfigurationNamespace, methodConfigurationName, txn) match {
      case None => RequestComplete(http.StatusCodes.NotFound, s"${methodConfigurationNamespace}/${methodConfigurationName} does not exist in ${workspaceContext}")
      case Some(methodConfiguration) => op(methodConfiguration)
    }
  }

  private def withMethod(workspaceContext: WorkspaceContext, methodNamespace: String, methodName: String, methodVersion: String, authCookie: HttpCookie)(op: (AgoraEntity) => PerRequestMessage): PerRequestMessage = {
    // TODO add Method to model instead of exposing AgoraEntity?
    methodRepoDAO.getMethod(methodNamespace, methodName, methodVersion, authCookie) match {
      case None => RequestComplete(http.StatusCodes.NotFound, s"Cannot get ${methodNamespace}/${methodName}/${methodVersion} from method repo.")
      case Some(agoraEntity) => op(agoraEntity)
    }
  }

  private def withWdl(method: AgoraEntity)(op: (String) => PerRequestMessage): PerRequestMessage = {
    method.payload match {
      case None => RequestComplete(StatusCodes.NotFound, "Can't get method's WDL from Method Repo: payload empty.")
      case Some(wdl) => op(wdl)
    }
  }

  private def withSubmission(workspaceContext: WorkspaceContext, submissionId: String, txn: RawlsTransaction)(op: (Submission) => PerRequestMessage): PerRequestMessage = {
    containerDAO.submissionDAO.get(workspaceContext, submissionId, txn) match {
      case None => RequestComplete(StatusCodes.NotFound, s"Submission with id ${submissionId} not found in workspace ${workspaceContext}")
      case Some(submission) => op(submission)
    }
  }

  private def withWorkflow(workspaceName: WorkspaceName, submission: Submission, workflowId: String)(op: (Workflow) => PerRequestMessage): PerRequestMessage = {
    submission.workflows.find(wf => wf.workflowId == workflowId) match {
      case None => RequestComplete(StatusCodes.NotFound, s"Workflow with id ${workflowId} not found in submission ${submission.submissionId} in workspace ${workspaceName.namespace}/${workspaceName.name}")
      case Some(workflow) => op(workflow)
    }
  }

  private def submitWorkflow(workspaceContext: WorkspaceContext, methodConfig: MethodConfiguration, entity: Entity, wdl: String, authCookie: HttpCookie, txn: RawlsTransaction) : Either[WorkflowFailure, Workflow] = {
    MethodConfigResolver.resolveInputsOrGatherErrors(workspaceContext, methodConfig, entity, wdl) match {
      case Left(failures) => Left(WorkflowFailure(entityName = entity.name, entityType = entity.entityType, errors = failures.map(AttributeString(_))))
      case Right(inputs) =>
        val execStatus = executionServiceDAO.submitWorkflow(wdl, MethodConfigResolver.propertiesToWdlInputs(inputs), authCookie)
        Right(Workflow(workflowId = execStatus.id, status = WorkflowStatuses.Submitted, statusLastChangedDate = DateTime.now, workflowEntity = AttributeEntityReference(entityName = entity.name, entityType = entity.entityType)))
    }
  }

  private def withSubmissionEntities(submissionRequest: SubmissionRequest, workspaceContext: WorkspaceContext, rootEntityType: String, txn: RawlsTransaction)(op: (Seq[Entity]) => PerRequestMessage): PerRequestMessage = {
    //If there's an expression, evaluate it to get the list of entities to run this job on.
    //Otherwise, use the entity given in the submission.
    submissionRequest.expression match {
      case None =>
        if ( submissionRequest.entityType != rootEntityType )
          RequestComplete(StatusCodes.BadRequest, s"Method configuration expects an entity of type ${rootEntityType}, but you gave us an entity of type ${submissionRequest.entityType}.")
        else
          containerDAO.entityDAO.get(workspaceContext,submissionRequest.entityType,submissionRequest.entityName,txn) match {
            case None =>
              RequestComplete(StatusCodes.NotFound, s"No entity of type ${submissionRequest.entityType} named ${submissionRequest.entityName} exists in this workspace.")
            case Some(entity) =>
              op(Seq(entity))
          }
      case Some(expression) =>
        new ExpressionEvaluator(new ExpressionParser()).evalFinalEntity(workspaceContext, submissionRequest.entityType, submissionRequest.entityName, expression) match {
          case Failure(regret) =>
            RequestComplete(StatusCodes.BadRequest, regret.getMessage)
          case Success(entities) =>
            if ( entities.isEmpty )
              RequestComplete(StatusCodes.BadRequest, "No entities eligible for submission were found.")
            else {
              val eligibleEntities = entities.filter(_.entityType == rootEntityType)
              if (eligibleEntities.isEmpty)
                RequestComplete(StatusCodes.BadRequest, s"The expression in your SubmissionRequest matched only entities of the wrong type. (Expected type ${rootEntityType}.)")
              else
                op(eligibleEntities)
            }
        }
    }
  }

  private def withSubmissionParameters(workspaceName: WorkspaceName, submissionRequest: SubmissionRequest)
   ( op: (RawlsTransaction, WorkspaceContext, MethodConfiguration, AgoraEntity, String, Seq[Entity]) => PerRequestMessage): PerRequestMessage = {
    dataSource inTransaction { txn =>
      withWorkspaceContextAndPermissions(workspaceName, WorkspaceAccessLevel.Write, txn) { workspaceContext =>
        withMethodConfig(workspaceContext, submissionRequest.methodConfigurationNamespace, submissionRequest.methodConfigurationName, txn) { methodConfig =>
          withMethod(workspaceContext, methodConfig.methodRepoMethod.methodNamespace, methodConfig.methodRepoMethod.methodName, methodConfig.methodRepoMethod.methodVersion, userInfo.authCookie) { agoraEntity =>
            withWdl(agoraEntity) { wdl =>
              withSubmissionEntities(submissionRequest, workspaceContext, methodConfig.rootEntityType, txn) { jobEntities =>
                op(txn, workspaceContext, methodConfig, agoraEntity, wdl, jobEntities)
              }
            }
          }
        }
      }
    }
  }
}

class AttributeUpdateOperationException(message: String) extends RawlsException(message)
class AttributeNotFoundException(message: String) extends AttributeUpdateOperationException(message)
