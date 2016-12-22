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

import org.joda.time.DateTime.now
import uk.gov.hmrc.apigateway.connector.impl.DelegatedAuthorityConnector
import uk.gov.hmrc.apigateway.exception.GatewayError.{InvalidCredentials, MissingCredentials}
import uk.gov.hmrc.apigateway.model.{Authority, ProxyRequest}
import uk.gov.hmrc.apigateway.util.HttpHeaders.AUTHORIZATION

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class DelegatedAuthorityFilter @Inject()(delegatedAuthorityConnector: DelegatedAuthorityConnector) {

  def filter(proxyRequest: ProxyRequest): Future[Authority] =
    getDelegatedAuthority(proxyRequest) map { authority =>
      if (hasExpired(authority))
        throw InvalidCredentials()
      authority
    }

  private def getDelegatedAuthority(proxyRequest: ProxyRequest): Future[Authority] =
    proxyRequest.getHeader(AUTHORIZATION) match {
      case Some(bearerToken) =>
        val accessToken = bearerToken.stripPrefix("Bearer ")
        delegatedAuthorityConnector.getByAccessToken(accessToken)
      case _ => throw MissingCredentials()
    }

  private def hasExpired(authority: Authority) =
    authority.delegatedAuthority.token.expiresAt.isBefore(now)

}
