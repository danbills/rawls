package org.broadinstitute.dsde.rawls.monitor

import akka.actor.SupervisorStrategy.{Resume, Escalate, Stop}
import akka.actor._
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.rawls.{RawlsExceptionWithErrorReport, RawlsException}
import org.broadinstitute.dsde.rawls.google.GooglePubSubDAO
import org.broadinstitute.dsde.rawls.google.GooglePubSubDAO.PubSubMessage
import org.broadinstitute.dsde.rawls.model._
import org.broadinstitute.dsde.rawls.monitor.GoogleGroupSyncMonitor.StartMonitorPass
import org.broadinstitute.dsde.rawls.monitor.GoogleGroupSyncMonitorSupervisor.{Init, Start}
import org.broadinstitute.dsde.rawls.user.UserService
import org.broadinstitute.dsde.rawls.util.addJitter
import spray.http.StatusCodes

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

import akka.pattern._

import spray.json._
import spray.json.DefaultJsonProtocol._
import org.broadinstitute.dsde.rawls.model.UserAuthJsonSupport._

/**
 * Created by dvoet on 12/6/16.
 */
object GoogleGroupSyncMonitorSupervisor {
  sealed trait GoogleGroupSyncMonitorSupervisorMessage
  case object Init extends GoogleGroupSyncMonitorSupervisorMessage
  case object Start extends GoogleGroupSyncMonitorSupervisorMessage

  def props(pollInterval: FiniteDuration, pollIntervalJitter: FiniteDuration, pubSubDao: GooglePubSubDAO, pubSubTopicName: String, pubSubSubscriptionName: String, workerCount: Int, userServiceConstructor: UserInfo => UserService)(implicit executionContext: ExecutionContext): Props = {
    Props(new GoogleGroupSyncMonitorSupervisor(pollInterval, pollIntervalJitter, pubSubDao, pubSubTopicName, pubSubSubscriptionName, workerCount, userServiceConstructor))
  }
}

class GoogleGroupSyncMonitorSupervisor(val pollInterval: FiniteDuration, pollIntervalJitter: FiniteDuration, pubSubDao: GooglePubSubDAO, pubSubTopicName: String, pubSubSubscriptionName: String, workerCount: Int, userServiceConstructor: UserInfo => UserService)(implicit executionContext: ExecutionContext) extends Actor with LazyLogging {
  import context._

  self ! Init

  override def receive = {
    case Init => init pipeTo self
    case Start => for(i <- 1 to workerCount) startOne()
    case Status.Failure(t) => logger.error("error initializing google group sync monitor", t)
  }

  def init = {
    for {
      _ <- pubSubDao.createTopic(pubSubTopicName)
      _ <- pubSubDao.createSubscription(pubSubTopicName, pubSubSubscriptionName)
    } yield Start
  }

  def startOne(): Unit = {
    logger.info("starting GoogleGroupSyncMonitorActor")
    actorOf(GoogleGroupSyncMonitor.props(pollInterval, pollIntervalJitter, pubSubDao, pubSubSubscriptionName, userServiceConstructor))
  }

  override val supervisorStrategy =
    OneForOneStrategy() {
      case e => {
        logger.error("error syncing google group", e)
        // start one to replace the error, stop the errored child so that we also drop its mailbox (i.e. restart not good enough)
        startOne()
        Stop
      }
    }

}

object GoogleGroupSyncMonitor {
  case object StartMonitorPass

  def props(pollInterval: FiniteDuration, pollIntervalJitter: FiniteDuration, pubSubDao: GooglePubSubDAO, pubSubSubscriptionName: String, userServiceConstructor: UserInfo => UserService)(implicit executionContext: ExecutionContext): Props = {
    Props(new GoogleGroupSyncMonitorActor(pollInterval, pollIntervalJitter, pubSubDao, pubSubSubscriptionName, userServiceConstructor))
  }
}

class GoogleGroupSyncMonitorActor(val pollInterval: FiniteDuration, pollIntervalJitter: FiniteDuration, pubSubDao: GooglePubSubDAO, pubSubSubscriptionName: String, userServiceConstructor: UserInfo => UserService)(implicit executionContext: ExecutionContext) extends Actor with LazyLogging {
  import context._

  self ! StartMonitorPass

  // fail safe in case this actor is idle too long but not too fast (1 second lower limit)
  setReceiveTimeout(max((pollInterval + pollIntervalJitter) * 10, 1 second))

  private def max(durations: FiniteDuration*): FiniteDuration = durations.max

  override def receive = {
    case StartMonitorPass =>
      // start the process by pulling a message and sending it back to self
      pubSubDao.pullMessages(pubSubSubscriptionName, 1).map(_.headOption) pipeTo self

    case Some(message: PubSubMessage) =>
      logger.debug(s"pulled $message")
      // send a message to the user service, it will send back a SyncReport message
      // note the message ack id used as the actor name
      // note that the UserInfo passed in probably is not used
      import org.broadinstitute.dsde.rawls.model.UserModelJsonSupport.RawlsGroupRefFormat
      val userServiceRef = actorOf(UserService.props(userServiceConstructor, UserInfo.buildFromTokens(pubSubDao.getPubSubServiceAccountCredential)), message.ackId)
      logger.debug(s"received sync message: ${message.contents}")
      userServiceRef ! UserService.InternalSynchronizeGroupMembers(message.contents.parseJson.convertTo[RawlsGroupRef])

    case None =>
      // there was no message to wait and try again
      val nextTime = addJitter(pollInterval, pollIntervalJitter)
      system.scheduler.scheduleOnce(nextTime.asInstanceOf[FiniteDuration], self, StartMonitorPass)

    case report: SyncReport =>
      val errorReports = report.items.collect {
        case SyncReportItem(_, _, errorReports) if errorReports.nonEmpty => errorReports
      }.flatten

      if (errorReports.isEmpty) {
        // sync done, log it and try again immediately
        acknowledgeMessage(sender()).map(_ => StartMonitorPass) pipeTo self
        logger.info(s"synchronized google group ${report.groupEmail.value}: ${report.items.toJson.compactPrint}")
      } else {
        throw new RawlsExceptionWithErrorReport(ErrorReport("error(s) syncing google group", errorReports))
      }

    case Status.Failure(t) =>
      t match {
        case groupNotFound: RawlsExceptionWithErrorReport if groupNotFound.errorReport.statusCode == Some(StatusCodes.NotFound) =>
          // this can happen if a group is created then removed before the sync message is handled
          // acknowledge it so we don't have to handle it again
          acknowledgeMessage(sender()).map(_ => StartMonitorPass) pipeTo self
          logger.info(s"group to synchronize not found: ${groupNotFound.errorReport}")

        case regrets: Throwable => throw regrets
      }

    case ReceiveTimeout =>
      throw new RawlsException("GoogleGroupSyncMonitorActor has received no messages for too long")
  }

  private def acknowledgeMessage(sender: ActorRef): Future[Unit] = {
    val messageAckId = sender.path.name
    // sender should be the user service actor instantiated up above, name of which is the message ack id
    stop(sender)
    pubSubDao.acknowledgeMessagesById(pubSubSubscriptionName, Seq(messageAckId))
  }

  override val supervisorStrategy =
    OneForOneStrategy() {
      case e => {
        Escalate
      }
    }
}
