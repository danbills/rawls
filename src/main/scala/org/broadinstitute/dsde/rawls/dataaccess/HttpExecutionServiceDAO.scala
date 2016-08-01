package org.broadinstitute.dsde.rawls.dataaccess

import akka.actor.ActorSystem
import akka.event.Logging
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.rawls.model._
import org.broadinstitute.dsde.rawls.model.ExecutionJsonSupport._
import org.broadinstitute.dsde.rawls.util.FutureSupport
import spray.client.pipelining

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import spray.client.pipelining._
import spray.http.{FormData, HttpRequest}
import spray.httpx.SprayJsonSupport._

import scala.util.Try

/**
 * @author tsharpe
 */
class HttpExecutionServiceDAO( executionServiceURL: String, submissionTimeout: FiniteDuration )( implicit val system: ActorSystem ) extends ExecutionServiceDAO with DsdeHttpDAO with Retry with FutureSupport with LazyLogging {

  def logRq: RequestTransformer = { req => logger.info(s"%s rq %s".format(this, req.toString)); req }
  def logRp: ResponseTransformer = { resp => logger.info(s"%s resp %s".format(this, resp.toString)); resp}

  override def submitWorkflows(wdl: String, inputs: Seq[String], options: Option[String], userInfo: UserInfo): Future[Seq[Either[ExecutionServiceStatus, ExecutionServiceFailure]]] = {
    implicit val timeout = Timeout(submissionTimeout)
    val url = executionServiceURL+"/workflows/v1/batch"
    import system.dispatcher

    val pipeline = addAuthHeader(userInfo) ~> logRq ~> sendReceive ~> logRp ~> unmarshal[Seq[Either[ExecutionServiceStatus, ExecutionServiceFailure]]]
    val formData = FormData(Seq("wdlSource" -> wdl, "workflowInputs" -> inputs.mkString("[", ",", "]")) ++ options.map("workflowOptions" -> _).toSeq)
    pipeline(Post(url,formData))
  }

  override def status(id: String, userInfo: UserInfo): Future[ExecutionServiceStatus] = {
    val url = executionServiceURL + s"/workflows/v1/${id}/status"
    import system.dispatcher
    val pipeline = addAuthHeader(userInfo) ~> logRq ~> sendReceive ~> logRp ~> unmarshal[ExecutionServiceStatus]
    retry(when500) { () => pipeline(Get(url)) }
  }

  override def callLevelMetadata(id: String, userInfo: UserInfo): Future[ExecutionMetadata] = {
    val url = executionServiceURL + s"/workflows/v1/${id}/metadata"
    import system.dispatcher
    val pipeline = addAuthHeader(userInfo) ~> logRq ~> sendReceive ~> logRp ~> unmarshal[ExecutionMetadata]
    retry(when500) { () => pipeline(Get(url)) }
  }

  override def outputs(id: String, userInfo: UserInfo): Future[ExecutionServiceOutputs] = {
    val url = executionServiceURL + s"/workflows/v1/${id}/outputs"
    import system.dispatcher
    val pipeline = addAuthHeader(userInfo) ~> logRq ~> sendReceive ~> logRp ~> unmarshal[ExecutionServiceOutputs]
    retry(when500) { () => pipeline(Get(url)) }
  }

  override def logs(id: String, userInfo: UserInfo): Future[ExecutionServiceLogs] = {
    val url = executionServiceURL + s"/workflows/v1/${id}/logs"
    import system.dispatcher
    val pipeline = addAuthHeader(userInfo) ~> logRq ~> sendReceive ~> logRp ~> unmarshal[ExecutionServiceLogs]
    retry(when500) { () => pipeline(Get(url)) }
  }

  override def abort(id: String, userInfo: UserInfo): Future[Try[ExecutionServiceStatus]] = {
    val url = executionServiceURL + s"/workflows/v1/${id}/abort"
    import system.dispatcher
    val pipeline = addAuthHeader(userInfo) ~> logRq ~> sendReceive ~> logRp ~> unmarshal[ExecutionServiceStatus]
    retry(when500) { () => toFutureTry(pipeline(Post(url))) }
  }

  private def when500( throwable: Throwable ): Boolean = {
    throwable match {
      case ure: spray.client.UnsuccessfulResponseException => ure.responseStatus.intValue/100 == 5
      case ure: spray.httpx.UnsuccessfulResponseException => ure.response.status.intValue/100 == 5
      case _ => false
    }
  }
}