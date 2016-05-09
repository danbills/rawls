package org.broadinstitute.dsde.rawls.expressions

import java.util.UUID

import akka.actor._
import akka.pattern._
import com.google.api.client.auth.oauth2.Credential
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.rawls.dataaccess.SlickDataSource
import org.broadinstitute.dsde.rawls.expressions.ExpressionParsingActor._
import org.broadinstitute.dsde.rawls.util.FutureSupport
import slick.dbio.DBIOAction

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.FiniteDuration

class HttpGooglePubSubDAO {
  //stub, to be replaced with veep code
}

object ExpressionParsingActor {
  def props(datasource: SlickDataSource,
            pubSubDAO: HttpGooglePubSubDAO,
            credential: Credential,
            pollInterval: FiniteDuration): Props = {
    Props(new ExpressionParsingActor(datasource, pubSubDAO, credential, pollInterval))
  }

  sealed trait ExpressionParsingMessage
  case object LookForASubmission extends ExpressionParsingMessage

}

class ExpressionParsingActor(val datasource: SlickDataSource,
                             val pubSubDAO: HttpGooglePubSubDAO,
                             val credential: Credential,
                             val pollInterval: FiniteDuration) extends Actor with ExpressionParsing with LazyLogging {

  import context._

  scheduleNextSubmissionQuery

  override def receive = {
    case LookForASubmission =>
      logger.debug(s"going to look for a submission rq in the database to evaluate yaaaaay")
      getSingleSubmission() pipeTo self

    case Status.Failure(t) => throw t // an error happened in some future, let the supervisor handle it
  }

  def scheduleNextSubmissionQuery: Cancellable = {
    system.scheduler.scheduleOnce(pollInterval, self, LookForASubmission)
  }
}

trait ExpressionParsing extends FutureSupport with LazyLogging {
  val datasource: SlickDataSource
  val pubSubDAO: HttpGooglePubSubDAO
  val credential: Credential
  val pollInterval: FiniteDuration

  def getSingleSubmission() (implicit executionContext: ExecutionContext): Future[ExpressionParsingMessage] = {
    datasource.inTransaction { txn =>

      //get one submission request that's in Accepted status
      //...and set it to Evaluating
      //commit the txn
      //now evaluate that expression


      DBIOAction.successful(LookForASubmission)
    }
  }
}