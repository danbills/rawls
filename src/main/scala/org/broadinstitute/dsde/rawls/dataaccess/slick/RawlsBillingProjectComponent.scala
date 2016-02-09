package org.broadinstitute.dsde.rawls.dataaccess.slick

import org.broadinstitute.dsde.rawls.RawlsException
import org.broadinstitute.dsde.rawls.model.{RawlsUserSubjectId, RawlsUserRef, RawlsBillingProjectName, RawlsBillingProject}

case class RawlsBillingProjectRecord(projectName: String, cromwellAuthBucketUrl: String)
case class ProjectUsersRecord(userSubjectId: String, projectName: String)

trait RawlsBillingProjectComponent extends Actions {
  this: DriverComponent
    with RawlsUserComponent =>

  import driver.api._

  class RawlsBillingProjectTable(tag: Tag) extends Table[RawlsBillingProjectRecord](tag, "BILLING_PROJECT") {
    def projectName = column[String]("NAME", O.PrimaryKey)
    def cromwellAuthBucketUrl = column[String]("CROMWELL_BUCKET_URL")

    def * = (projectName, cromwellAuthBucketUrl) <> (RawlsBillingProjectRecord.tupled, RawlsBillingProjectRecord.unapply)
  }

  class ProjectUsersTable(tag: Tag) extends Table[ProjectUsersRecord](tag, "PROJECT_USERS") {
    def userSubjectId = column[String]("USER_SUBJECT_ID")
    def projectName = column[String]("PROJECT_NAME")

    def * = (userSubjectId, projectName) <> (ProjectUsersRecord.tupled, ProjectUsersRecord.unapply)

    def user = foreignKey("FK_PROJECT_USERS_USER", userSubjectId, rawlsUserQuery)(_.userSubjectId)
    def project = foreignKey("FK_PROJECT_USERS_PROJECT", projectName, rawlsBillingProjectQuery)(_.projectName)
    def pk = primaryKey("PK_PROJECT_USERS", (userSubjectId, projectName))
  }

  protected val projectUsersQuery = TableQuery[ProjectUsersTable]
  private type RawlsBillingProjectQuery = Query[RawlsBillingProjectTable, RawlsBillingProjectRecord, Seq]
  private type ProjectUsersQuery = Query[ProjectUsersTable, ProjectUsersRecord, Seq]

  object rawlsBillingProjectQuery extends TableQuery(new RawlsBillingProjectTable(_)) {

    def save(billingProject: RawlsBillingProject): WriteAction[RawlsBillingProject] = {
      val projectInsert = rawlsBillingProjectQuery insertOrUpdate marshalBillingProject(billingProject)
      val userInserts = billingProject.users.toSeq.map {
        projectUsersQuery insertOrUpdate marshalProjectUsers(_, billingProject)
      }

      projectInsert andThen findUsersByProjectName(billingProject.projectName.value).delete andThen DBIO.seq(userInserts: _*) map { _ => billingProject }
    }

    def load(rawlsProjectName: RawlsBillingProjectName): ReadAction[Option[RawlsBillingProject]] = {
      val name = rawlsProjectName.value
      findBillingProjectByName(name).result.flatMap {
        case Seq() => DBIO.successful(None)
        case Seq(projectRec) =>
          for {
            users <- findUsersByProjectName(name).result
          } yield Option(unmarshalBillingProject(projectRec, users.toSet))
        case _ => throw new RawlsException(s"Primary key violation: found multiple records for billing project '$name'")
      }
    }

    def delete(billingProject: RawlsBillingProject): ReadWriteAction[Boolean] = {
      val name = billingProject.projectName.value
      val projectQuery = findBillingProjectByName(name)

      projectQuery.result.flatMap {
        case Seq() => DBIO.successful(false)
        case Seq(projectRec) =>
          findUsersByProjectName(name).delete andThen projectQuery.delete map { count => count > 0 }
        case _ => throw new RawlsException(s"Primary key violation: found multiple records for billing project '$name'")
      }
    }

    def addUserToProject(userRef: RawlsUserRef, billingProject: RawlsBillingProject): WriteAction[ProjectUsersRecord] = {
      val record = marshalProjectUsers(userRef, billingProject)
      projectUsersQuery insertOrUpdate record map { _ => record }
    }

    def removeUserFromProject(userRef: RawlsUserRef, billingProject: RawlsBillingProject): WriteAction[Boolean] = {
      val query = projectUsersQuery.filter(q => q.userSubjectId === userRef.userSubjectId.value && q.projectName === billingProject.projectName.value)
      query.delete.map { count => count > 0 }
    }

    def listUserProjects(rawlsUser: RawlsUserRef): ReadAction[Iterable[RawlsBillingProjectName]] = {
      findProjectsByUserSubjectId(rawlsUser.userSubjectId.value).result.map { projects =>
        projects.map { rec => RawlsBillingProjectName(rec.projectName) }
      }
    }

    private def marshalBillingProject(billingProject: RawlsBillingProject): RawlsBillingProjectRecord = {
      RawlsBillingProjectRecord(billingProject.projectName.value, billingProject.cromwellAuthBucketUrl)
    }

    private def unmarshalBillingProject(projectRecord: RawlsBillingProjectRecord, userRecords: Set[ProjectUsersRecord]): RawlsBillingProject = {
      val userRefs = userRecords.map { u => RawlsUserRef(RawlsUserSubjectId(u.userSubjectId)) }
      RawlsBillingProject(RawlsBillingProjectName(projectRecord.projectName), userRefs, projectRecord.cromwellAuthBucketUrl)
    }

    private def marshalProjectUsers(userRef: RawlsUserRef, billingProject: RawlsBillingProject): ProjectUsersRecord = {
      ProjectUsersRecord(userRef.userSubjectId.value, billingProject.projectName.value)
    }

    private def findBillingProjectByName(name: String): RawlsBillingProjectQuery = {
      rawlsBillingProjectQuery.filter(_.projectName === name)
    }

    private def findUsersByProjectName(name: String): ProjectUsersQuery = {
      projectUsersQuery.filter(_.projectName === name)
    }

    private def findProjectsByUserSubjectId(subjectId: String): ProjectUsersQuery = {
      projectUsersQuery.filter(_.userSubjectId === subjectId)
    }
  }
}

