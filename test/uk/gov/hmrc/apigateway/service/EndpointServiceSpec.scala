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

import org.joda.time.DateTimeUtils
import org.joda.time.DateTimeUtils._
import org.mockito.Mockito.{verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.test.FakeRequest
import uk.gov.hmrc.apigateway.connector.impl.ApiDefinitionConnector
import uk.gov.hmrc.apigateway.exception.GatewayError.{MatchingResourceNotFound, NotFound}
import uk.gov.hmrc.apigateway.model.AuthType.NONE
import uk.gov.hmrc.apigateway.model._
import uk.gov.hmrc.apigateway.util.HttpHeaders.ACCEPT
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future
import scala.concurrent.Future._

class EndpointServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private val apiDefinitionConnector = mock[ApiDefinitionConnector]
  private val endpointService = new EndpointService(apiDefinitionConnector)
  private val apiDefinition = ApiDefinition(
    "api-context", "http://host.example", Seq(ApiVersion("1.0", Seq(ApiEndpoint("/api-endpoint", "GET", NONE))))
  )
  private val fixedTimeInMillis = 11223344

  override def beforeEach(): Unit = {
    setCurrentMillisFixed(fixedTimeInMillis)
  }

  override def afterEach(): Unit = {
    DateTimeUtils.setCurrentMillisSystem()
  }

  "Endpoint service" should {

    val request = FakeRequest("GET", "/api-context/api-endpoint").withHeaders(ACCEPT -> "application/vnd.hmrc.1.0+json")
    val requestWithQueryString = FakeRequest("GET", "/api-context/api-endpoint?requiredParam=value").withHeaders(ACCEPT -> "application/vnd.hmrc.1.0+json")

    "invoke api definition connector with correct service name" in {
      mockApiServiceConnectorToReturnSuccess

      await(endpointService.apiRequest(ProxyRequest(request), request))
      verify(apiDefinitionConnector).getByContext("api-context")
    }

    "return api definition when proxy request matches api definition endpoint" in {
      mockApiServiceConnectorToReturnSuccess

      val beforeTimeNanos = System.nanoTime()
      val actualApiRequest = await(endpointService.apiRequest(ProxyRequest(request), request))
      val afterTimeNanos = System.nanoTime()

      assertApiRequest(apiRequest(), actualApiRequest, beforeTimeNanos, afterTimeNanos)
    }

    "fail with NotFound when no version matches the Accept headers in the API Definition" in {
      val notFoundRequest = request.withHeaders("Accept" -> "application/vnd.hmrc.55.0+json")

      mockApiServiceConnectorToReturnSuccess

      intercept[NotFound]{
        await(endpointService.apiRequest(ProxyRequest(notFoundRequest), notFoundRequest))
      }
    }

    "fail with MatchingResourceNotFound when no endpoint matches in the API Definition" in {
      val invalidRequest = FakeRequest("GET", "/api-context/invalidEndpoint").withHeaders(ACCEPT -> "application/vnd.hmrc.1.0+json")

      mockApiServiceConnectorToReturnSuccess

      intercept[MatchingResourceNotFound]{
        await(endpointService.apiRequest(ProxyRequest(invalidRequest), invalidRequest))
      }
    }

    "fail with MatchingResourceNotFound when a required request parameter is not in the URL" in {

      val anApiDefinition = ApiDefinition("api-context", "http://host.example", Seq(ApiVersion("1.0",
        Seq(ApiEndpoint("/api-endpoint", "GET", NONE, queryParameters = Some(Seq(Parameter("requiredParam", required = true))))))))

      mockApiServiceConnectorToReturn("api-context", successful(anApiDefinition))

      intercept[MatchingResourceNotFound]{
        await(endpointService.apiRequest(ProxyRequest(request), request))
      }
    }

    "succeed when all required request parameters are in the URL" in {

      val anApiDefinition = ApiDefinition("api-context", "http://host.example", Seq(ApiVersion("1.0",
        Seq(ApiEndpoint("/api-endpoint", "GET", NONE, queryParameters = Some(Seq(Parameter("requiredParam", required = true))))))))

      mockApiServiceConnectorToReturn("api-context", successful(anApiDefinition))

      val beforeTimeNanos = System.nanoTime()
      val actualApiRequest = await(endpointService.apiRequest(ProxyRequest(requestWithQueryString), requestWithQueryString))
      val afterTimeNanos = System.nanoTime()

      assertApiRequest(apiRequest(urlWithQueryString), actualApiRequest, beforeTimeNanos, afterTimeNanos)
    }

    "succeed when all request parameters in the URL are not required" in {

      val anApiDefinition = ApiDefinition("api-context", "http://host.example", Seq(ApiVersion("1.0",
        Seq(ApiEndpoint("/api-endpoint", "GET", NONE, queryParameters = Some(Seq(Parameter("requiredParam", required = false))))))))

      mockApiServiceConnectorToReturn("api-context", successful(anApiDefinition))

      val beforeTimeNanos = System.nanoTime()
      val actualApiRequest = await(endpointService.apiRequest(ProxyRequest(requestWithQueryString), requestWithQueryString))
      val afterTimeNanos = System.nanoTime()

      assertApiRequest(apiRequest(urlWithQueryString), actualApiRequest, beforeTimeNanos, afterTimeNanos)
    }

    "succeed when at least one required request parameter is in the URL" in {

      val anApiDefinition = ApiDefinition("api-context", "http://host.example", Seq(ApiVersion("1.0",
        Seq(ApiEndpoint("/api-endpoint", "GET", NONE, queryParameters = Some(Seq(Parameter("requiredParam", required = true), Parameter("anotherRequiredParam", required = true))))))))

      mockApiServiceConnectorToReturn("api-context", successful(anApiDefinition))

      val beforeTimeNanos = System.nanoTime()
      val actualApiRequest = await(endpointService.apiRequest(ProxyRequest(requestWithQueryString), requestWithQueryString))
      val afterTimeNanos = System.nanoTime()

      assertApiRequest(apiRequest(urlWithQueryString), actualApiRequest, beforeTimeNanos, afterTimeNanos)
    }

    "throw an exception when proxy request does not match api definition endpoint" in {

      mockApiServiceConnectorToReturnFailure

      intercept[RuntimeException] {
        await(endpointService.apiRequest(ProxyRequest(request), request))
      }
    }

  }

  private def assertApiRequest(expectedApiRequest: ApiRequest, actualApiRequest: ApiRequest,
                               actualNanoTimeBeforeExec: Long, actualNanoTimeAfterExec: Long) = {

    actualApiRequest.timeInNanos.get should (be >= actualNanoTimeBeforeExec and be <= actualNanoTimeAfterExec)
    actualApiRequest.copy(timeInNanos = None) shouldBe expectedApiRequest.copy(timeInNanos = None)
  }

  private val basicUrl = "http://host.example//api-context/api-endpoint"
  private val urlWithQueryString = "http://host.example//api-context/api-endpoint?requiredParam=value"

  private def apiRequest(url: String = basicUrl) = ApiRequest(
    timeInMillis = Some(fixedTimeInMillis),
    timeInNanos = Some(System.nanoTime()),
    apiIdentifier = ApiIdentifier("api-context", "1.0"),
    apiEndpoint = url
  )

  private def mockApiServiceConnectorToReturnSuccess =
    mockApiServiceConnectorToReturn("api-context", successful(apiDefinition))

  private def mockApiServiceConnectorToReturnFailure =
    mockApiServiceConnectorToReturn("api-context", failed(new RuntimeException("simulated test exception")))

  private def mockApiServiceConnectorToReturn(context: String, eventualApiDefinition: Future[ApiDefinition]) =
    when(apiDefinitionConnector.getByContext(context)).thenReturn(eventualApiDefinition)

}
