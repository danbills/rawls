package org.broadinstitute.dsde.rawls

import java.io.StringReader
import java.net.InetAddress
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.codahale.metrics.SharedMetricRegistries
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.json.jackson2.JacksonFactory
import com.readytalk.metrics.{StatsDReporter, WorkbenchStatsD}
import com.typesafe.config.{ConfigFactory, ConfigObject}
import com.typesafe.scalalogging.LazyLogging
import slick.backend.DatabaseConfig
import slick.driver.JdbcDriver
import org.broadinstitute.dsde.rawls.dataaccess._
import org.broadinstitute.dsde.rawls.dataaccess.jndi.DirectoryConfig
import org.broadinstitute.dsde.rawls.genomics.GenomicsService
import org.broadinstitute.dsde.rawls.google.HttpGooglePubSubDAO
import org.broadinstitute.dsde.rawls.model.{ApplicationVersion, UserInfo}
import org.broadinstitute.dsde.rawls.monitor._
import org.broadinstitute.dsde.rawls.statistics.StatisticsService
import org.broadinstitute.dsde.rawls.status.StatusService
import org.broadinstitute.dsde.rawls.user.UserService
import org.broadinstitute.dsde.rawls.util.ScalaConfig._
import org.broadinstitute.dsde.rawls.util._
import org.broadinstitute.dsde.rawls.webservice._
import org.broadinstitute.dsde.rawls.workspace.WorkspaceService
import spray.can.Http

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Boot extends App with LazyLogging {
  private def startup(): Unit = {
    // version.conf is generated by sbt
    val conf = ConfigFactory.parseResources("version.conf").withFallback(ConfigFactory.load())
    val gcsConfig = conf.getConfig("gcs")

    // we need an ActorSystem to host our application in
    implicit val system = ActorSystem("rawls")

    val directoryConfig = DirectoryConfig(
      conf.getString("directory.url"),
      conf.getString("directory.user"),
      conf.getString("directory.password"),
      conf.getString("directory.baseDn")
    )

    val slickDataSource = DataSource(DatabaseConfig.forConfig[JdbcDriver]("slick", conf), directoryConfig)

    val liquibaseConf = conf.getConfig("liquibase")
    val liquibaseChangeLog = liquibaseConf.getString("changelog")
    val initWithLiquibase = liquibaseConf.getBoolean("initWithLiquibase")

    val changelogParams = Map("gcs:appsDomain" -> gcsConfig.getString("appsDomain"))

    if(initWithLiquibase) {
      slickDataSource.initWithLiquibase(liquibaseChangeLog, changelogParams)
    }

    val metricsConf = conf.getConfig("metrics")
    val metricsPrefix = {
      val basePrefix = metricsConf.getString("prefix")
      metricsConf.getBooleanOption("includeHostname") match {
        case Some(true) =>
          val hostname = InetAddress.getLocalHost().getHostName()
          basePrefix + "." + hostname
        case _ => basePrefix
      }
    }

    if (metricsConf.getBooleanOption("enabled").getOrElse(false)) {
      metricsConf.getObjectOption("reporters") match {
        case Some(configObject) =>
          configObject.entrySet.map(_.toTuple).foreach {
            case ("statsd", conf: ConfigObject) =>
              val statsDConf = conf.toConfig
              startStatsDReporter(
                statsDConf.getString("host"),
                statsDConf.getInt("port"),
                statsDConf.getDuration("period"),
                apiKey = statsDConf.getStringOption("apiKey"))
            case (other, _) =>
              logger.warn(s"Unknown metrics backend: $other")
          }
        case None => logger.info("No metrics reporters defined")
      }
    } else {
      logger.info("Metrics reporting is disabled.")
    }

    val jsonFactory = JacksonFactory.getDefaultInstance
    val clientSecrets = GoogleClientSecrets.load(jsonFactory, new StringReader(gcsConfig.getString("secrets")))
    val billingClientSecrets = GoogleClientSecrets.load(jsonFactory, new StringReader(gcsConfig.getString("billingSecrets")))
    val gcsDAO = new HttpGoogleServicesDAO(
      false,
      clientSecrets,
      gcsConfig.getString("pathToPem"),
      gcsConfig.getString("appsDomain"),
      gcsConfig.getString("groupsPrefix"),
      gcsConfig.getString("appName"),
      gcsConfig.getInt("deletedBucketCheckSeconds"),
      gcsConfig.getString("serviceProject"),
      gcsConfig.getString("tokenEncryptionKey"),
      gcsConfig.getString("tokenSecretsJson"),
      billingClientSecrets,
      gcsConfig.getString("billingPemEmail"),
      gcsConfig.getString("pathToBillingPem"),
      gcsConfig.getString("billingEmail"),
      gcsConfig.getInt("bucketLogsMaxAge"),
      workbenchMetricBaseName = metricsPrefix
    )

    val pubSubDAO = new HttpGooglePubSubDAO(
      clientSecrets.getDetails.get("client_email").toString,
      gcsConfig.getString("pathToPem"),
      gcsConfig.getString("appName"),
      gcsConfig.getString("serviceProject"),
      workbenchMetricBaseName = metricsPrefix
    )

    val samConfig = conf.getConfig("sam")
    val samDAO = new HttpSamDAO(samConfig.getString("server"), gcsDAO.getBucketServiceAccountCredential)

    enableServiceAccount(gcsDAO, samDAO)

    system.registerOnTermination {
      slickDataSource.databaseConfig.db.shutdown
    }

    val executionServiceConfig = conf.getConfig("executionservice")
    val submissionTimeout = util.toScalaDuration(executionServiceConfig.getDuration("workflowSubmissionTimeout"))

    val executionServiceServers: Map[ExecutionServiceId, ExecutionServiceDAO] = executionServiceConfig.getObject("readServers").map {
        case (strName, strHostname) => (ExecutionServiceId(strName)->new HttpExecutionServiceDAO(strHostname.unwrapped.toString, submissionTimeout, metricsPrefix))
      }.toMap

    val executionServiceSubmitServers: Map[ExecutionServiceId, ExecutionServiceDAO] = executionServiceConfig.getObject("submitServers").map {
      case (strName, strHostname) => (ExecutionServiceId(strName)->new HttpExecutionServiceDAO(strHostname.unwrapped.toString, submissionTimeout, metricsPrefix))
    }.toMap

    val shardedExecutionServiceCluster:ExecutionServiceCluster = new ShardedHttpExecutionServiceCluster(executionServiceServers, executionServiceSubmitServers, slickDataSource)

    val projectOwners = gcsConfig.getStringList("projectTemplate.owners")
    val projectEditors = gcsConfig.getStringList("projectTemplate.editors")
    val projectServices = gcsConfig.getStringList("projectTemplate.services")
    val projectOwnerGrantableRoles = gcsConfig.getStringList("projectTemplate.ownerGrantableRoles")
    val projectTemplate = ProjectTemplate(Map("roles/owner" -> projectOwners, "roles/editor" -> projectEditors), projectServices)

    val notificationDAO = new PubSubNotificationDAO(pubSubDAO, gcsConfig.getString("notifications.topicName"))

    val userServiceConstructor: (UserInfo) => UserService = UserService.constructor(slickDataSource, gcsDAO, pubSubDAO, gcsConfig.getString("groupMonitor.topicName"),  notificationDAO, samDAO, projectOwnerGrantableRoles)

    val genomicsServiceConstructor: (UserInfo) => GenomicsService = GenomicsService.constructor(slickDataSource, gcsDAO)
    val statisticsServiceConstructor: (UserInfo) => StatisticsService = StatisticsService.constructor(slickDataSource, gcsDAO)
    val agoraConfig = conf.getConfig("methodrepo")
    val methodRepoDAO = new HttpMethodRepoDAO(agoraConfig.getString("server"), agoraConfig.getString("path"), metricsPrefix)

    val maxActiveWorkflowsTotal = conf.getInt("executionservice.maxActiveWorkflowsPerServer") * executionServiceServers.size
    val maxActiveWorkflowsPerUser = maxActiveWorkflowsTotal / conf.getInt("executionservice.activeWorkflowHogFactor")

    if(conf.getBooleanOption("backRawls").getOrElse(false)) {
      logger.info("This instance has been marked as BACK. Booting monitors...")
      BootMonitors.bootMonitors(
        system, conf, slickDataSource, gcsDAO, samDAO, pubSubDAO, methodRepoDAO, shardedExecutionServiceCluster,
        maxActiveWorkflowsTotal, maxActiveWorkflowsPerUser, userServiceConstructor, projectTemplate, metricsPrefix
      )
    } else logger.info("This instance has been marked as FRONT. Monitors will not be booted...")

    val healthMonitor = system.actorOf(
      HealthMonitor.props(
        slickDataSource,
        gcsDAO,
        pubSubDAO,
        methodRepoDAO,
        samDAO,
        executionServiceServers,
        groupsToCheck = Seq(gcsDAO.adminGroupName, gcsDAO.curatorGroupName),
        topicsToCheck = Seq(gcsConfig.getString("notifications.topicName"), gcsConfig.getString("groupMonitor.topicName")),
        bucketsToCheck = Seq(gcsDAO.tokenBucketName)
      ).withDispatcher("health-monitor-dispatcher"),
      "health-monitor"
    )
    logger.info("Starting health monitor...")
    system.scheduler.schedule(10 seconds, 1 minute, healthMonitor, HealthMonitor.CheckAll)

    val statusServiceConstructor: () => StatusService = StatusService.constructor(healthMonitor)

    val service = system.actorOf(RawlsApiServiceActor.props(
      WorkspaceService.constructor(
        slickDataSource,
        methodRepoDAO,
        shardedExecutionServiceCluster,
        conf.getInt("executionservice.batchSize"),
        gcsDAO,
        samDAO,
        notificationDAO,
        userServiceConstructor,
        genomicsServiceConstructor,
        maxActiveWorkflowsTotal,
        maxActiveWorkflowsPerUser,
        workbenchMetricBaseName = metricsPrefix),
      userServiceConstructor,
      genomicsServiceConstructor,
      statisticsServiceConstructor,
      statusServiceConstructor,
      shardedExecutionServiceCluster,
      ApplicationVersion(conf.getString("version.git.hash"), conf.getString("version.build.number"), conf.getString("version.version")),
      clientSecrets.getDetails.getClientId,
      submissionTimeout,
      rawlsMetricBaseName = metricsPrefix,
      samDAO
    ),
      "rawls-service")

    implicit val timeout = Timeout(20.seconds)
    // start a new HTTP server on port 8080 with our service actor as the handler
    (IO(Http) ? Http.Bind(service, interface = "0.0.0.0", port = 8080)).onComplete {
      case Success(Http.CommandFailed(failure)) =>
        system.log.error("could not bind to port: " + failure.toString)
        system.shutdown()
      case Failure(t) =>
        system.log.error(t, "could not bind to port")
        system.shutdown()
      case _ =>
    }
  }

  /**
   * Enables the rawls service account in ldap. Allows service to service auth through the proxy.
   * @param gcsDAO
   */
  def enableServiceAccount(gcsDAO: HttpGoogleServicesDAO, samDAO: HttpSamDAO): Unit = {
    val credential = gcsDAO.getBucketServiceAccountCredential
    val serviceAccountUserInfo = UserInfo.buildFromTokens(credential)

    val registerServiceAccountFuture = samDAO.registerUser(serviceAccountUserInfo)

    registerServiceAccountFuture.onFailure {
      // this is logged as a warning because almost always the service account is already enabled
      // so this is a problem only the first time rawls is started with a new service account
      case t: Throwable => logger.warn("error enabling service account", t)
    }
  }

  def startStatsDReporter(host: String, port: Int, period: java.time.Duration, registryName: String = "default", apiKey: Option[String] = None): Unit = {
    logger.info(s"Starting statsd reporter writing to [$host:$port] with period [${period.toMillis} ms]")
    val reporter = StatsDReporter.forRegistry(SharedMetricRegistries.getOrCreate(registryName))
      .prefixedWith(apiKey.orNull)
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .build(WorkbenchStatsD(host, port))
    reporter.start(period.toMillis, period.toMillis, TimeUnit.MILLISECONDS)
  }

  startup()
}
