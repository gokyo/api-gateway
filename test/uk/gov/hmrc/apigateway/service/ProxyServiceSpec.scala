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

package uk.gov.hmrc.apigateway.service

import org.joda.time.DateTimeUtils._
import org.mockito.BDDMockito.given
import org.mockito.Mockito.{timeout, verify, verifyZeroInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import uk.gov.hmrc.apigateway.connector.impl.ProxyConnector
import uk.gov.hmrc.apigateway.model.AuthType.NONE
import uk.gov.hmrc.apigateway.util.RequestTags._
import uk.gov.hmrc.play.test.UnitSpec

class ProxyServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  trait Setup {
    val proxyConnector = mock[ProxyConnector]
    val auditService = mock[AuditService]
    val underTest = new ProxyService(proxyConnector, auditService)
  }

  val microserviceEndpoint = "http://hello-world.service/hello/world"
  val request = FakeRequest("GET", "/hello/world").copyFakeRequest(
    tags = Map(
      API_ENDPOINT -> microserviceEndpoint,
      AUTH_TYPE -> "USER"
    )
  )

  override def beforeEach() = {
    setCurrentMillisFixed(10000)
  }

  override def afterEach() = {
    setCurrentMillisSystem()
  }

  "proxy" should {

    "call and return the response from the microservice" in new Setup {
      val response = Ok("hello")

      given(proxyConnector.proxy(request, microserviceEndpoint)).willReturn(response)

      val result = await(underTest.proxy(request))

      result shouldBe response
    }

    "audit the request" in new Setup {

      val response = Ok("hello")

      given(proxyConnector.proxy(request, microserviceEndpoint)).willReturn(response)

      await(underTest.proxy(request))

      verify(auditService, timeout(2000)).auditSuccessfulRequest(request, response)
    }

    "not audit the request for open endpoint" in new Setup {
      val openRequest = request.copyFakeRequest(tags = request.tags + (AUTH_TYPE -> NONE.toString))

      given(proxyConnector.proxy(openRequest, microserviceEndpoint)).willReturn(Ok("hello"))

      await(underTest.proxy(openRequest))

      verifyZeroInteractions(auditService)
    }
  }
}
