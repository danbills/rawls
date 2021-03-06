package org.broadinstitute.dsde.rawls.webservice

import java.util.concurrent.TimeUnit

import akka.actor.PoisonPill
import org.broadinstitute.dsde.rawls.RawlsTestUtils
import org.broadinstitute.dsde.rawls.dataaccess._
import org.broadinstitute.dsde.rawls.dataaccess.slick.TestDriverComponentWithFlatSpecAndMatchers
import org.broadinstitute.dsde.rawls.genomics.GenomicsService
import org.broadinstitute.dsde.rawls.google.MockGooglePubSubDAO
import org.broadinstitute.dsde.rawls.jobexec.SubmissionSupervisor
import org.broadinstitute.dsde.rawls.mock.RemoteServicesMockServer
import org.broadinstitute.dsde.rawls.model.RawlsUser
import org.broadinstitute.dsde.rawls.monitor.{BucketDeletionMonitor, GoogleGroupSyncMonitorSupervisor, HealthMonitor}
import org.broadinstitute.dsde.rawls.statistics.StatisticsService
import org.broadinstitute.dsde.rawls.status.StatusService
import org.broadinstitute.dsde.rawls.user.UserService
import org.broadinstitute.dsde.rawls.workspace.WorkspaceService
import spray.http.{ContentTypes, HttpEntity, StatusCodes}
import spray.httpx.SprayJsonSupport
import spray.json._
import spray.routing._
import spray.testkit.ScalatestRouteTest

import scala.concurrent.duration._

// common trait to be inherited by API service tests
trait ApiServiceSpec extends TestDriverComponentWithFlatSpecAndMatchers with HttpService with ScalatestRouteTest with SprayJsonSupport with RawlsTestUtils {
  // increate the timeout for ScalatestRouteTest from the default of 1 second, otherwise
  // intermittent failures occur on requests not completing in time
  implicit val routeTestTimeout = RouteTestTimeout(5.seconds)

  def actorRefFactory = system

  val mockServer = RemoteServicesMockServer()

  override def beforeAll(): Unit = {
    super.beforeAll()
    mockServer.startServer
  }

  override def afterAll(): Unit = {
    mockServer.stopServer
    super.afterAll()
  }

  def httpJsonStr(str: String) = HttpEntity(ContentTypes.`application/json`, str)
  def httpJson[T](obj: T)(implicit writer: JsonWriter[T]) = httpJsonStr(obj.toJson.toString())
  val httpJsonEmpty = httpJsonStr("[]")

  def revokeCuratorRole(services: ApiServices, user: RawlsUser = testData.userOwner): Unit = {
    Get("/user/role/curator") ~>
      sealRoute(services.userRoutes) ~>
      check {
        assertResult(StatusCodes.OK) {
          status
        }
      }

    Delete(s"/admin/user/role/curator/${user.userEmail.value}") ~>
      sealRoute(services.adminRoutes) ~>
      check {
        assertResult(StatusCodes.OK) {
          status
        }
      }

    Get("/user/role/curator") ~>
      sealRoute(services.userRoutes) ~>
      check {
        assertResult(StatusCodes.NotFound) {
          status
        }
      }
  }

  trait ApiServices extends AdminApiService with EntityApiService with MethodConfigApiService with SubmissionApiService with UserApiService with WorkspaceApiService with BillingApiService with NotificationsApiService with StatusApiService {
    val dataSource: SlickDataSource
    val gcsDAO: MockGoogleServicesDAO
    val gpsDAO: MockGooglePubSubDAO

    def actorRefFactory = system

    val submissionTimeout = FiniteDuration(1, TimeUnit.MINUTES)

    val executionServiceCluster = MockShardedExecutionServiceCluster.fromDAO(new HttpExecutionServiceDAO(mockServer.mockServerBaseUrl, mockServer.defaultWorkflowSubmissionTimeout), slickDataSource)

    val submissionSupervisor = system.actorOf(SubmissionSupervisor.props(
      executionServiceCluster,
      slickDataSource
    ).withDispatcher("submission-monitor-dispatcher"))

    val bucketDeletionMonitor = system.actorOf(BucketDeletionMonitor.props(
      slickDataSource,
      gcsDAO
    ))

    val directoryDAO = new MockUserDirectoryDAO

    val googleGroupSyncTopic = "test-topic-name"

    val notificationTopic = "test-notification-topic"
    val notificationDAO = new PubSubNotificationDAO(gpsDAO, notificationTopic)

    val userServiceConstructor = UserService.constructor(
      slickDataSource,
      gcsDAO,
      directoryDAO,
      gpsDAO,
      googleGroupSyncTopic,
      notificationDAO
    )_

    val googleGroupSyncMonitorSupervisor = system.actorOf(GoogleGroupSyncMonitorSupervisor.props(500 milliseconds, 0 seconds, gpsDAO, googleGroupSyncTopic, "test-sub-name", 1, userServiceConstructor))

    val genomicsServiceConstructor = GenomicsService.constructor(
      slickDataSource,
      gcsDAO,
      directoryDAO
    )_

    val statisticsServiceConstructor = StatisticsService.constructor(
      slickDataSource,
      gcsDAO,
      directoryDAO
    )_

    val methodRepoDAO = new HttpMethodRepoDAO(mockServer.mockServerBaseUrl)

    val healthMonitor = system.actorOf(HealthMonitor.props(
      dataSource, gcsDAO, gpsDAO, directoryDAO, methodRepoDAO,
      Seq.empty, Seq.empty, Seq.empty))
    val statusServiceConstructor = StatusService.constructor(healthMonitor)_

    val execServiceBatchSize = 3
    val maxActiveWorkflowsTotal = 10
    val maxActiveWorkflowsPerUser = 2
    val workspaceServiceConstructor = WorkspaceService.constructor(
      slickDataSource,
      methodRepoDAO,
      executionServiceCluster,
      execServiceBatchSize,
      gcsDAO,
      notificationDAO,
      submissionSupervisor,
      bucketDeletionMonitor,
      userServiceConstructor,
      genomicsServiceConstructor,
      maxActiveWorkflowsTotal,
      maxActiveWorkflowsPerUser
    )_

    def cleanupSupervisor = {
      submissionSupervisor ! PoisonPill
      googleGroupSyncMonitorSupervisor ! PoisonPill
      bucketDeletionMonitor ! PoisonPill
    }
  }

}
