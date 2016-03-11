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
import org.broadinstitute.dsde.rawls.webservice.PerRequest.PerRequestMessage
import org.broadinstitute.dsde.rawls.webservice.{SubmissionApiService, MethodConfigApiService, EntityApiService, WorkspaceApiService}
import org.broadinstitute.dsde.rawls.workspace.WorkspaceService
import org.joda.time.DateTime
import org.scalatest.{Matchers, FlatSpec}
import spray.http.OAuth2BearerToken
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
class WorkspaceServiceSpec extends FlatSpec with ScalatestRouteTest with Matchers with OrientDbTestFixture {

  val gcsDAO: MockGoogleServicesDAO = new MockGoogleServicesDAO("test")

  class ManyWorkspaces() extends TestData {
    val userOwner = RawlsUser(UserInfo("owner-access", OAuth2BearerToken("token"), 123, "123456789876543212345"))
    val userWriter = RawlsUser(UserInfo("writer-access", OAuth2BearerToken("token"), 123, "123456789876543212346"))
    val userReader = RawlsUser(UserInfo("reader-access", OAuth2BearerToken("token"), 123, "123456789876543212347"))

    val realmGroup = makeRawlsGroup(s"SuperSecretRealmGroup", Set(userOwner, userReader, userWriter), Set.empty)

    override def save(txn: RawlsTransaction): Unit = {
      authDAO.saveUser(userOwner, txn)
      authDAO.saveUser(userWriter, txn)
      authDAO.saveUser(userReader, txn)
      authDAO.saveGroup(realmGroup, txn)
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

  /*


    1. need a user service
      - steal the user service constructor from WorkspaceServiceSpec

    2. remove a user from the realm
      - UserService.updateGroupMembers(groupRef: RawlsGroupRef, memberList: RawlsGroupMemberList, operation: UpdateGroupMembersOp)

    3. this should throw a java ConcurrentModificationException in a big-ass callstack in Orient
      - because we're calculating multiple intersection groups at the same time on separate threads
     */
  "UserService" should "recalculate multiple intersection groups at once" in withUserService { services =>
    services.dataSource.inTransaction() { txn =>

      //Maybe this is a better approach:
      //val foob: IndexedSeq[Future[Future[PerRequestMessage]]] =

      val ctxFutures: Future[Traversable[WorkspaceContext]] = Future.traverse(1 to 20) { case _ =>
        services.workspaceService.createNewWorkspace(
          WorkspaceRequest("manyWorkspaces", UUID.randomUUID.toString, Some(manyWorkspaces.realmGroup), Map.empty), txn)
      }

      Await.result( ctxFutures flatMap { ctxList =>
        Future.traverse(ctxList) { wsctx =>
            services.workspaceService.updateACL(wsctx.workspace.toWorkspaceName, Seq(
              WorkspaceACLUpdate(manyWorkspaces.userOwner.userEmail.value, WorkspaceAccessLevels.Owner),
              WorkspaceACLUpdate(manyWorkspaces.userWriter.userEmail.value, WorkspaceAccessLevels.Read),
              WorkspaceACLUpdate(manyWorkspaces.userOwner.userEmail.value, WorkspaceAccessLevels.Write)
            ))
        }
      }, Duration.Inf )

      
      val fnarr = Await.result( services.userService.removeGroupMembers(
        manyWorkspaces.realmGroup,
        RawlsGroupMemberList(Some(Seq(manyWorkspaces.userReader.userEmail.value)), None, None, None)
      ), Duration.Inf )
      print("womble")
    }


  }
}
