/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.apigateway.connector

import play.api.Logger
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.libs.json.Format
import play.api.libs.ws.WSClient
import uk.gov.hmrc.apigateway.cache.EntityWithResponseHeaders
import uk.gov.hmrc.apigateway.exception.GatewayError.{NotFound, ServerError}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag

abstract class AbstractConnector(wsClient: WSClient) {

  def get[T: ClassTag](url: String)(implicit format: Format[T]): Future[T] = {
    get(url, Seq.empty[(String, String)]) map (_._1)
  }

  def get[T: ClassTag](url: String, headers: Seq[(String, String)])(implicit format: Format[T]): Future[EntityWithResponseHeaders[T]] = {
    wsClient.url(url).withHeaders(headers: _*).get() map {
      case wsResponse if wsResponse.status >= OK && wsResponse.status < 300 =>
        Logger.debug(s"GET $url ${wsResponse.status}")
        (wsResponse.json.as[T], wsResponse.allHeaders.mapValues(strings => strings.mkString(",")))

      case wsResponse if wsResponse.status == NOT_FOUND =>
        Logger.debug(s"GET $url ${wsResponse.status}")
        throw NotFound()

      case wsResponse =>
        Logger.error(s"Response status not handled: GET $url ${wsResponse.status} ${wsResponse.body}")
        throw ServerError()
    }
  }
}
