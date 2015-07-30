package org.broadinstitute.dsde.rawls.webservice

import org.broadinstitute.dsde.rawls.model._
import org.broadinstitute.dsde.rawls.openam.OpenAmDirectives
import org.broadinstitute.dsde.rawls.workspace.WorkspaceService
import spray.routing.Directive.pimpApply
import spray.routing._

/**
 * Created by dvoet on 6/4/15.
 */

trait MethodConfigApiService extends HttpService with PerRequestCreator with OpenAmDirectives {
  lazy private implicit val executionContext = actorRefFactory.dispatcher

  import org.broadinstitute.dsde.rawls.model.WorkspaceJsonSupport._
  import spray.httpx.SprayJsonSupport._

  val workspaceServiceConstructor: UserInfo => WorkspaceService

  val methodConfigRoutes = userInfoFromCookie() { userInfo =>
    path("workspaces" / Segment / Segment / "methodconfigs") { (workspaceNamespace, workspaceName) =>
      post {
        entity(as[MethodConfiguration]) { methodConfiguration =>
          requestContext => perRequest(requestContext, WorkspaceService.props(workspaceServiceConstructor, userInfo),
            WorkspaceService.CreateMethodConfiguration(workspaceNamespace, workspaceName, methodConfiguration))
        }
      }
    } ~
    path("workspaces" / Segment / Segment / "methodconfigs" / Segment / Segment) { (workspaceNamespace, workspaceName, methodConfigurationNamespace, methodConfigName) =>
      get {
        requestContext => perRequest(requestContext, WorkspaceService.props(workspaceServiceConstructor, userInfo),
          WorkspaceService.GetMethodConfiguration(workspaceNamespace, workspaceName, methodConfigurationNamespace, methodConfigName))
      }
    } ~
    path("workspaces" / Segment / Segment / "methodconfigs") { (workspaceNamespace, workspaceName) =>
      get {
        requestContext => perRequest(requestContext, WorkspaceService.props(workspaceServiceConstructor, userInfo),
          WorkspaceService.ListMethodConfigurations(workspaceNamespace, workspaceName))
      }
    } ~
    path("workspaces" / Segment / Segment / "methodconfigs" / Segment / Segment) { (workspaceNamespace, workspaceName, methodConfigurationNamespace, methodConfigName) =>
      delete {
        requestContext => perRequest(requestContext, WorkspaceService.props(workspaceServiceConstructor, userInfo),
          WorkspaceService.DeleteMethodConfiguration(workspaceNamespace, workspaceName, methodConfigurationNamespace, methodConfigName))
      }
    } ~
    path("workspaces" / Segment / Segment / "methodconfigs" / Segment / Segment / "rename") { (workspaceNamespace, workspaceName, methodConfigurationNamespace, methodConfigurationName) =>
      post {
        entity(as[MethodConfigurationName]) { newEntityName =>
          requestContext => perRequest(requestContext, WorkspaceService.props(workspaceServiceConstructor, userInfo),
            WorkspaceService.RenameMethodConfiguration(workspaceNamespace, workspaceName, methodConfigurationNamespace, methodConfigurationName, newEntityName.name))
        }
      }
    } ~
    path("workspaces" / Segment / Segment / "methodconfigs" / Segment / Segment) { (workspaceNamespace, workspaceName, methodConfigurationNamespace, methodConfigName) =>
      put {
        entity(as[MethodConfiguration]) { newMethodConfiguration =>
          requestContext => perRequest(requestContext, WorkspaceService.props(workspaceServiceConstructor, userInfo),
            WorkspaceService.UpdateMethodConfiguration(workspaceNamespace, workspaceName, newMethodConfiguration.copy(namespace = methodConfigurationNamespace, name = methodConfigName)))
        }
      }
    } ~
    path("methodconfigs" / "copy") {
      post {
        entity(as[MethodConfigurationNamePair]) { confNames =>
          requestContext => perRequest(requestContext, WorkspaceService.props(workspaceServiceConstructor, userInfo),
            WorkspaceService.CopyMethodConfiguration(confNames))
        }
      }
    } ~
    path("methodconfigs" / "copyFromMethodRepo") {
      post {
        entity(as[MethodRepoConfigurationQuery]) { query =>
          requestContext => perRequest(requestContext, WorkspaceService.props(workspaceServiceConstructor, userInfo),
            WorkspaceService.CopyMethodConfigurationFromMethodRepo(query))
        }
      }
    }
  }
}