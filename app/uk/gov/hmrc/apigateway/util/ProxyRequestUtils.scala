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

package uk.gov.hmrc.apigateway.util

import uk.gov.hmrc.apigateway.exception.GatewayError.NotFound
import uk.gov.hmrc.apigateway.model.ProxyRequest
import uk.gov.hmrc.apigateway.util.HttpHeaders._

import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}
import scala.util.matching.Regex

object ProxyRequestUtils {

  private val parseContext = firstGroup("""\/([^\/]*).*""".r)
  private val parseVersion = firstGroup("""application\/vnd\.hmrc\.(.*)\+.*""".r)
  private val defaultVersion = "1.0"

  def validateContext[T](proxyRequest: ProxyRequest): Future[String] =
    validateOrElse(parseContext(proxyRequest.rawPath), NotFound())

  def parseVersion[T](proxyRequest: ProxyRequest): Future[String] = {
    val acceptHeader: String = proxyRequest.getHeader(ACCEPT).getOrElse("")
    successful(parseVersion(acceptHeader).getOrElse(defaultVersion))
  }

  private def validateOrElse(maybeString: Option[String], throwable: Throwable): Future[String] =
    maybeString map successful getOrElse failed(throwable)

  private def firstGroup(regex: Regex) = { value: String =>
    regex.unapplySeq(value) flatMap (_.headOption)
  }

}
