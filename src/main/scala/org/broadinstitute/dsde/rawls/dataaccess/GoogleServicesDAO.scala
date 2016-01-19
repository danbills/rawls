package org.broadinstitute.dsde.rawls.dataaccess

import akka.actor.ActorRef
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import org.broadinstitute.dsde.rawls.model.WorkspaceAccessLevels._
import org.broadinstitute.dsde.rawls.model._
import org.joda.time.DateTime
import spray.http.StatusCodes
import scala.concurrent.Future

abstract class GoogleServicesDAO(groupsPrefix: String) {

  // returns a workspaceID
  def setupWorkspace(userInfo: UserInfo, projectId: String, workspaceId: String, workspaceName: WorkspaceName): Future[Unit]

  def createCromwellAuthBucket(billingProject: RawlsBillingProjectName): Future[String]

  def deleteWorkspace(bucketName: String, monitorRef: ActorRef): Future[Any]

  def deleteBucket(bucketName: String, monitorRef: ActorRef): Future[Any]

  def getACL(workspaceId: String): Future[WorkspaceACL]

  def updateACL(currentUser: UserInfo, workspaceId: String, aclUpdates: Map[Either[RawlsUser, RawlsGroup], WorkspaceAccessLevel]): Future[Option[Seq[ErrorReport]]]

  def getMaximumAccessLevel(userId: String, workspaceId: String): Future[WorkspaceAccessLevel]

  def getBucketName(workspaceId: String) = s"${groupsPrefix}-${workspaceId}"

  def getCromwellAuthBucketName(billingProject: RawlsBillingProjectName) = s"cromwell-auth-${billingProject.value}"

  def isAdmin(userId: String): Future[Boolean]

  def addAdmin(userId: String): Future[Unit]

  def deleteAdmin(userId: String): Future[Unit]

  def listAdmins(): Future[Seq[String]]

  /**
   *
   * @param groupRef
   * @return None if the google group does not exist, Some(Seq.empty) if there are no members
   */
  def listGroupMembers(groupRef: RawlsGroupRef): Future[Option[Set[Either[RawlsUserRef, RawlsGroupRef]]]]

  def createProxyGroup(user: RawlsUser): Future[Unit]

  def addUserToProxyGroup(user: RawlsUser): Future[Unit]

  def removeUserFromProxyGroup(user: RawlsUser): Future[Unit]

  def isUserInProxyGroup(user: RawlsUser): Future[Boolean]

  def createGoogleGroup(groupRef: RawlsGroupRef): Future[Unit]

  def addMemberToGoogleGroup(groupRef: RawlsGroupRef, member: Either[RawlsUser, RawlsGroup]): Future[Unit]

  def removeMemberFromGoogleGroup(groupRef: RawlsGroupRef, memberToAdd: Either[RawlsUser, RawlsGroup]): Future[Unit]

  def deleteGoogleGroup(groupRef: RawlsGroupRef): Future[Unit]

  def storeToken(userInfo: UserInfo, refreshToken: String): Future[Unit]
  def getToken(rawlsUserRef: RawlsUserRef): Future[Option[String]]
  def getTokenDate(userInfo: UserInfo): Future[Option[DateTime]]
  def deleteToken(userInfo: UserInfo): Future[Unit]

  def toProxyFromUser(userSubjectId: RawlsUserSubjectId): String
  def toUserFromProxy(proxy: String): String
  def toGoogleGroupName(groupName: RawlsGroupName): String

  def toErrorReport(throwable: Throwable) = {
    val SOURCE = "google"
    throwable match {
      case gjre: GoogleJsonResponseException =>
        val statusCode = StatusCodes.getForKey(gjre.getStatusCode)
        ErrorReport(SOURCE,ErrorReport.message(gjre),statusCode,ErrorReport.causes(gjre),Seq.empty)
      case _ =>
        ErrorReport(SOURCE,ErrorReport.message(throwable),None,ErrorReport.causes(throwable),throwable.getStackTrace)
    }
  }

  def getUserCredentials(rawlsUserRef: RawlsUserRef): Future[Option[Credential]]
}
