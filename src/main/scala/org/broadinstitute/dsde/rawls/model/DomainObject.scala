package org.broadinstitute.dsde.rawls.model

import org.broadinstitute.dsde.rawls.model.SubmissionStatuses.SubmissionStatus
import org.broadinstitute.dsde.rawls.model.WorkflowStatuses.WorkflowStatus
import org.broadinstitute.dsde.rawls.model.WorkspaceAccessLevels.WorkspaceAccessLevel
import org.joda.time.DateTime

import scala.reflect.runtime.universe._
import scala.reflect.runtime.{universe=>ru}

sealed trait DomainObject {
  //the names of the fields on this object that uniquely identify it relative to any graph siblings
  def idFields: Seq[String]

  def getFieldValue(tpe: Type, field: String): String = {
    val mirror = ru.runtimeMirror(this.getClass.getClassLoader)
    val idFieldSym = tpe.decl(ru.TermName(field)).asMethod
    mirror.reflect(this).reflectField(idFieldSym).get.asInstanceOf[String]
  }
}

case class Workspace(
                      namespace: String,
                      name: String,
                      workspaceId: String,
                      bucketName: String,
                      createdDate: DateTime,
                      lastModified: DateTime,
                      createdBy: String,
                      attributes: Map[String, Attribute],
                      accessLevels: Map[WorkspaceAccessLevel, RawlsGroupRef],
                      isLocked: Boolean = false
                      ) extends Attributable with DomainObject {
  def toWorkspaceName = WorkspaceName(namespace,name)
  def briefName = toWorkspaceName.toString
  def idFields = Seq("name")
}

case class EntityName(name: String)

case class Entity(
                   name: String,
                   entityType: String,
                   attributes: Map[String, Attribute]
                   ) extends Attributable with DomainObject {
  def briefName = name
  def path( workspaceName: WorkspaceName ) = s"${workspaceName.path}/entities/${name}"
  def idFields = Seq("name")
}

case class MethodRepoMethod(
                             methodNamespace: String,
                             methodName: String,
                             methodVersion: Int
                             ) extends DomainObject {
  def idFields = Seq("methodName")
}

case class MethodConfiguration(
                                namespace: String,
                                name: String,
                                rootEntityType: String,
                                prerequisites: Map[String, AttributeString],
                                inputs: Map[String, AttributeString],
                                outputs: Map[String, AttributeString],
                                methodRepoMethod:MethodRepoMethod
                                ) extends DomainObject {
  def toShort : MethodConfigurationShort = MethodConfigurationShort(name, rootEntityType, methodRepoMethod, namespace)
  def path( workspaceName: WorkspaceName ) = workspaceName.path+s"/methodConfigs/${namespace}/${name}"
  def idFields = Seq("name", "namespace")
}

case class MethodConfigurationShort(
                                     name: String,
                                     rootEntityType: String,
                                     methodRepoMethod:MethodRepoMethod,
                                     namespace: String) extends DomainObject {
  def idFields = Seq("name")
}

// Status of a successfully started workflow
case class Workflow(
                     workflowId: String,
                     status: WorkflowStatus,
                     statusLastChangedDate: DateTime,
                     workflowEntity: AttributeEntityReference,
                     inputResolutions: Seq[SubmissionValidationValue],
                     messages: Seq[AttributeString] = Seq.empty
                     ) extends DomainObject {
  def idFields = Seq("workflowId")
}

// Encapsulating errors for workflows that failed to start
case class WorkflowFailure(
                            entityName: String,
                            entityType: String,
                            inputResolutions: Seq[SubmissionValidationValue],
                            errors: Seq[AttributeString]
                            ) extends DomainObject {
  def idFields = Seq("entityName")
}

// Status of a submission
case class Submission(
                       submissionId: String,
                       submissionDate: DateTime,
                       submitter: RawlsUserRef,
                       methodConfigurationNamespace: String,
                       methodConfigurationName: String,
                       submissionEntity: AttributeEntityReference,
                       workflows: Seq[Workflow],
                       notstarted: Seq[WorkflowFailure],
                       status: SubmissionStatus
                       ) extends DomainObject {
  def idFields = Seq("submissionId")
}

// result of an expression parse
case class SubmissionValidationValue(
                                      value: Option[Attribute],
                                      error: Option[String],
                                      inputName: String
                                      ) extends DomainObject {
  def idFields = Seq("inputName")
}

case class RawlsUser(userSubjectId: RawlsUserSubjectId, userEmail: RawlsUserEmail) extends DomainObject {
  def idFields = Seq("userSubjectId")
}

object RawlsUser {
  implicit def toRef(u: RawlsUser) = RawlsUserRef(u.userSubjectId)

  def apply(userInfo: UserInfo): RawlsUser =
    RawlsUser(RawlsUserSubjectId(userInfo.userSubjectId), RawlsUserEmail(userInfo.userEmail))
}

case class RawlsGroup(groupName: RawlsGroupName, groupEmail: RawlsGroupEmail, users: Set[RawlsUserRef], subGroups: Set[RawlsGroupRef]) extends DomainObject {
  def idFields = Seq("groupName")
}

object RawlsGroup {
  implicit def toRef(g: RawlsGroup) = RawlsGroupRef(g.groupName)

  // for Workspace Access Groups
  def apply(workspaceName: WorkspaceName, accessLevel: WorkspaceAccessLevel): RawlsGroup =
    apply(workspaceName, accessLevel, Set.empty[RawlsUserRef], Set.empty[RawlsGroupRef])

  // for Workspace Access Groups
  def apply(workspaceName: WorkspaceName, accessLevel: WorkspaceAccessLevel, users: Set[RawlsUserRef], groups: Set[RawlsGroupRef]): RawlsGroup = {
    val name = RawlsGroupName(UserAuth.toWorkspaceAccessGroupName(workspaceName, accessLevel))
    RawlsGroup(name, RawlsGroupEmail(""), users, groups)
  }
}

case class RawlsBillingProject(projectName: RawlsBillingProjectName, users: Set[RawlsUserRef], cromwellAuthBucketUrl: String) extends DomainObject {
  def idFields = Seq("projectName")
}
