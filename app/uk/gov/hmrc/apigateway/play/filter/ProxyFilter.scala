/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.apigateway.play.filter

import javax.inject.{Inject, Singleton}

import akka.stream.Materializer
import play.api.Logger
import play.api.libs.json.Json._
import play.api.mvc.Results._
import play.api.mvc._
import uk.gov.hmrc.apigateway.exception.GatewayError
import uk.gov.hmrc.apigateway.exception.GatewayError.{NotFound => _, _}
import uk.gov.hmrc.apigateway.model.ProxyRequest
import uk.gov.hmrc.apigateway.play.binding.PlayBindings._
import uk.gov.hmrc.apigateway.util.HttpHeaders.{ACCEPT, X_API_GATEWAY_ENDPOINT}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ProxyFilter @Inject()
(endpointMatchFilter: EndpointMatchFilter, delegatedAuthorityFilter: DelegatedAuthorityFilter, scopeValidationFilter: ScopeValidationFilter)
(implicit override val mat: Materializer, executionContext: ExecutionContext) extends Filter {

  override def apply(nextFilter: (RequestHeader) => Future[Result])(requestHeader: RequestHeader) = {
    val proxyRequest = ProxyRequest(requestHeader)

    val eventualProxiedRequestHeader: Future[RequestHeader] = for {
      apiDefinitionMatch <- endpointMatchFilter.filter(proxyRequest)
      xApiGatewayEndpoint = s"${apiDefinitionMatch.serviceBaseUrl}/${proxyRequest.path}"
      delegatedAuthority <- delegatedAuthorityFilter.filter(proxyRequest) // TODO: this step must be skipped for open endpoints
      // TODO implement rate limit filter
      // TODO implement subscription filter
      isValidScope <- scopeValidationFilter.filter(delegatedAuthority, apiDefinitionMatch)
      // TODO implement token swap
      proxiedRequestHeader = requestHeader
        .withTag(ACCEPT, proxyRequest.headers(ACCEPT))
        .withTag(X_API_GATEWAY_ENDPOINT, xApiGatewayEndpoint)
    } yield proxiedRequestHeader

    eventualProxiedRequestHeader.flatMap(nextFilter) recover {
      case e: MissingCredentials => Unauthorized(toJson(e))
      case e: InvalidCredentials => Unauthorized(toJson(e))
      case e: InvalidAcceptHeader => BadRequest(toJson(e))
      case e: MatchingResourceNotFound => NotFound(toJson(e))
      case e: InvalidScope => Forbidden(toJson(e))
      case e: GatewayError => NotFound(toJson(e))
      case e =>
        Logger.error("unexpected error", e)
        InternalServerError(toJson(ServerError()))
    }
  }

}
