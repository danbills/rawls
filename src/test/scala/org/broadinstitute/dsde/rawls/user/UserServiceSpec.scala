package org.broadinstitute.dsde.rawls.user

import akka.testkit.TestActorRef
import org.broadinstitute.dsde.rawls.dataaccess._
import org.broadinstitute.dsde.rawls.db.TestData
import org.broadinstitute.dsde.rawls.graph.OrientDbTestFixture
import org.broadinstitute.dsde.rawls.jobexec.SubmissionSupervisor
import org.broadinstitute.dsde.rawls.mock.RemoteServicesMockServer
import org.broadinstitute.dsde.rawls.model._
import org.broadinstitute.dsde.rawls.model.RawlsGroupMemberList
import org.broadinstitute.dsde.rawls.monitor.BucketDeletionMonitor
import org.broadinstitute.dsde.rawls.openam.MockUserInfoDirectives
import org.broadinstitute.dsde.rawls.user.UserService.RemoveGroupMembers
import org.broadinstitute.dsde.rawls.webservice.PerRequest.{RequestCompleteWithLocation, PerRequestMessage}
import org.broadinstitute.dsde.rawls.webservice.{SubmissionApiService, MethodConfigApiService, EntityApiService, WorkspaceApiService}
import org.broadinstitute.dsde.rawls.workspace.WorkspaceService
import org.joda.time.DateTime
import org.scalatest.{Matchers, FlatSpec}
import spray.http.{StatusCodes, OAuth2BearerToken}
import spray.testkit.ScalatestRouteTest
import java.util.UUID

import scala.collection.immutable.IndexedSeq
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * Created with IntelliJ IDEA.
  * User: hussein
  * Date: 03/10/2016
  * Time: 14:52
  */
class UserServiceSpec extends FlatSpec with ScalatestRouteTest with Matchers with OrientDbTestFixture {

  val gcsDAO: MockGoogleServicesDAO = new MockGoogleServicesDAO("test")

  class ManyWorkspaces() extends TestData {
    val userOwner = RawlsUser(UserInfo("owner-access", OAuth2BearerToken("token"), 123, "123456789876543212345"))
    val userWriter = RawlsUser(UserInfo("writer-access", OAuth2BearerToken("token"), 123, "123456789876543212346"))
    val userReader = RawlsUser(UserInfo("reader-access", OAuth2BearerToken("token"), 123, "123456789876543212347"))

    val billingProject = RawlsBillingProject(RawlsBillingProjectName("manyWorkspaces"), Set(userOwner, userWriter, userReader), "cromwellBucketWhoCares")

    override def save(txn: RawlsTransaction): Unit = {
      authDAO.saveUser(userOwner, txn)
      authDAO.saveUser(userWriter, txn)
      authDAO.saveUser(userReader, txn)
      try {
        billingDAO.saveProject(billingProject, txn)
      } catch {
        case e:RuntimeException =>
          println(e.getMessage)
      }

    }
  }

  val manyWorkspaces = new ManyWorkspaces

  case class TestApiService(dataSource: DataSource)(implicit val executionContext: ExecutionContext) extends MockUserInfoDirectives {
    def actorRefFactory = system

    lazy val userService: UserService = TestActorRef(UserService.props(userServiceConstructor, userInfo)).underlyingActor
    lazy val workspaceService: WorkspaceService = TestActorRef(WorkspaceService.props(workspaceServiceConstructor, userInfo)).underlyingActor
    val mockServer = RemoteServicesMockServer()

    val directoryDAO = new MockUserDirectoryDAO
    val submissionSupervisor = system.actorOf(SubmissionSupervisor.props(
      containerDAO,
      new HttpExecutionServiceDAO(mockServer.mockServerBaseUrl),
      dataSource
    ).withDispatcher("submission-monitor-dispatcher"), "test-ws-submission-supervisor")
    val bucketDeletionMonitor = system.actorOf(BucketDeletionMonitor.props(dataSource, containerDAO, gcsDAO))

    val userServiceConstructor = UserService.constructor(
      dataSource,
      gcsDAO,
      containerDAO,
      directoryDAO
    ) _

    val workspaceServiceConstructor = WorkspaceService.constructor(
      dataSource,
      containerDAO,
      new HttpMethodRepoDAO(mockServer.mockServerBaseUrl),
      new HttpExecutionServiceDAO(mockServer.mockServerBaseUrl),
      gcsDAO,
      submissionSupervisor,
      bucketDeletionMonitor,
      userServiceConstructor
    )_
  }

  def withUserService(testCode: TestApiService => Any): Unit = {
    withManyWorkspacesTestDatabase { dataSource =>
      val apiService = new TestApiService(dataSource)
      testCode(apiService)
    }
  }

  def withManyWorkspacesTestDatabase(testCode: DataSource => Any): Unit = {
    withCustomTestDatabase(manyWorkspaces)(testCode)
  }

  "UserService" should "recalculate multiple intersection groups at once" in withUserService { services =>
    //Have to make the realm group here authDAO.saveGroup() doesn't make the Googles :(
    val realmGroupRef = RawlsGroupRef(RawlsGroupName(s"SuperSecretRealmGroup"))
    Await.result({ services.userService.createGroup(realmGroupRef) }, Duration.Inf)
    val realmGroupList = RawlsGroupMemberList(
      Some(
        Seq(
          manyWorkspaces.userOwner.userEmail.value,
          manyWorkspaces.userWriter.userEmail.value,
          manyWorkspaces.userReader.userEmail.value)
      ), None, None, None)
    Await.result({ services.userService.overwriteGroupMembers(realmGroupRef, realmGroupList )}, Duration.Inf )

    //make a bunch of workspaces that are all in the same realm
    val workspaceList: Seq[Workspace] = (1 to 20) map { case _ =>
      Await.result({
        services.workspaceService.createWorkspace(
          WorkspaceRequest("manyWorkspaces", UUID.randomUUID.toString, Some(realmGroupRef), Map.empty)
        )}, Duration.Inf)
      match {
        case RequestCompleteWithLocation((status, workspace: Workspace), path) =>
          assert(status == StatusCodes.Created) //shoulda made a workspace successfully
          workspace
      }
    }

    //populate acls for these workspaces, and wait for it to be done
    workspaceList foreach { workspace =>
      Await.result({
        services.workspaceService.updateACL(workspace.toWorkspaceName, Seq(
            WorkspaceACLUpdate(manyWorkspaces.userOwner.userEmail.value, WorkspaceAccessLevels.Owner),
            WorkspaceACLUpdate(manyWorkspaces.userReader.userEmail.value, WorkspaceAccessLevels.Read),
            WorkspaceACLUpdate(manyWorkspaces.userWriter.userEmail.value, WorkspaceAccessLevels.Write)
          ))
      }, Duration.Inf)
    }

    //the test itself: recalculating intersection groups for many workspaces after a realm change
    //shouldn't throw java.util.ConcurrentModificationException
    Await.result( services.userService.removeGroupMembers(
      realmGroupRef,
      RawlsGroupMemberList(Some(Seq(manyWorkspaces.userReader.userEmail.value)), None, None, None)
    ), Duration.Inf )
  }
}
