package org.broadinstitute.dsde.rawls.dataaccess.slick

import java.util.UUID

import org.broadinstitute.dsde.rawls.model.{WorkspacePermission, RawlsUserSubjectId}
import org.broadinstitute.dsde.rawls.model.WorkspacePermission.{WorkspacePermission, WorkspaceUserPermissions, WorkspaceUserPermission}

/**
 * Created by mbemis on 1/5/17.
 */

case class WorkspacePermissionRecord(permissionId: Long, name: String)
case class WorkspaceUserPermissionRecord(workspaceId: UUID, subjectId: String, permissionId: Long)

trait RawlsUserPermissionsComponent {

  this: DriverComponent
    with WorkspaceComponent
    with RawlsUserComponent =>

  import driver.api._

  class WorkspaceSharePermissionTable(tag: Tag) extends Table[WorkspaceUserPermissionRecord](tag, "WORKSPACE_SHARE_PERMISSIONS") {
    def workspaceId = column[UUID]("workspace_id")
    def userSubjectId = column[String]("user_subject_id")
    def permissionId = column[Long]("permission_id", O.Length(254))

    def workspace = foreignKey("FK_WS_PERMS_WS", workspaceId, workspaceQuery)(_.id)
    def user = foreignKey("FK_WS_PERMS_USER", userSubjectId, rawlsUserQuery)(_.userSubjectId)
    def permission = foreignKey("FK_WS_PERMS_PERM", permissionId, workspacePermissionsQuery)(_.id)

    def * = (workspaceId, userSubjectId, permissionId) <> (WorkspaceUserPermissionRecord.tupled, WorkspaceUserPermissionRecord.unapply)
  }

  object rawlsUserPermissionsQuery extends TableQuery(new WorkspaceUserPermissionTable(_)) {

    def getUserWorkspacePermissions(workspaceId: UUID, subjectId: RawlsUserSubjectId): ReadAction[Seq[WorkspacePermission]] = {

      val query = (filter(rec => rec.userSubjectId === subjectId.value && rec.workspaceId === workspaceId) join workspacePermissionsQuery on (_.permissionId === _.id) map (rec => (rec._1.workspaceId, rec._1.userSubjectId, rec._2.name)))

      query.result.map(x => x.map(y => WorkspacePermission.withName(y._3)))
    }

//    def updateUserPermissions(permissions: RawlsUserPermissionsRecord): ReadWriteAction[RawlsUserPermissionsRecord] = {
//      this.insertOrUpdate(permissions) map { _ => permissions }
//    }
  }
}
