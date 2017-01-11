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

package it.uk.gov.hmrc.apigateway.feature

import java.util.UUID

import it.uk.gov.hmrc.apigateway.BaseFeatureSpec
import org.joda.time.DateTime.now
import play.api.http.Status._
import uk.gov.hmrc.apigateway.model._
import uk.gov.hmrc.apigateway.util.HttpHeaders.{ACCEPT, AUTHORIZATION}

import scalaj.http.Http

class RequestAuthorizationIntegrationSpec extends BaseFeatureSpec {

  private val wrongSubscriptions = Seq(API(
    context = "context",
    versions = Seq(Subscription(Version("4.0"), subscribed = true)))
  )

  private val correctSubscriptions = Seq(API(
    context = "api-simulator",
    versions = Seq(Subscription(Version("1.0"), subscribed = true)))
  )

  private val anApiDefinition = ApiDefinition("api-simulator", api.url,
    Seq(
      ApiVersion("1.0", Seq(
        ApiEndpoint("userScope1", "GET", AuthType.USER, scope = Some("scope1")),
        ApiEndpoint("userScope2", "GET", AuthType.USER, scope = Some("scope2")),
        ApiEndpoint("application", "GET", AuthType.APPLICATION),
        ApiEndpoint("open", "GET", AuthType.NONE))
      ))
    )
  private val apiResponse = """{"response": "ok"}"""
  private val accessToken = "token"

  private val authority = Authority(
    ThirdPartyDelegatedAuthority("authBearerToken", "clientId", Token(accessToken, Set("scope1"), now().plusHours(3))),
    authExpired = false)

  private val applicationId = UUID.randomUUID()
  private val application = Application(applicationId, "appName")

  override def beforeEach() {
    super.beforeEach()

    Given("An API Definition exists")
    apiDefinition.willReturnTheApiDefinition(anApiDefinition)

    And("The API returns a response")
    api.willReturnTheResponse(apiResponse)
  }

  feature("User Restricted endpoint") {

    scenario("A user restricted request without an 'authorization' http header is not proxied") {

      Given("A request without an 'authorization' http header")
      val httpRequest = Http(s"$serviceUrl/api-simulator/userScope1").header(ACCEPT, "application/vnd.hmrc.1.0+json")

      When("The request is sent to the gateway")
      val httpResponse = invoke(httpRequest)

      Then("The http response is '401' unauthorized")
      assertCodeIs(httpResponse, UNAUTHORIZED)

      And("The response message code is 'MISSING_CREDENTIALS'")
      assertBodyIs(httpResponse, """ {"code":"MISSING_CREDENTIALS","message":"Authentication information is not provided"} """)
    }

    scenario("A user restricted request with an invalid 'authorization' http header is not proxied") {

      Given("A request with an invalid access token")
      val httpRequest = Http(s"$serviceUrl/api-simulator/userScope1")
        .header(ACCEPT, "application/vnd.hmrc.1.0+json")
        .header(AUTHORIZATION, s"Bearer $accessToken")

      And("An authority does not exist for the access token")
      thirdPartyDelegatedAuthority.willNotReturnAnAuthorityForAccessToken(accessToken)

      When("The request is sent to the gateway")
      val httpResponse = invoke(httpRequest)

      Then("The http response is '401' unauthorized")
      assertCodeIs(httpResponse, UNAUTHORIZED)

      And("The response message code is 'INVALID_CREDENTIALS'")
      assertBodyIs(httpResponse, """ {"code":"INVALID_CREDENTIALS","message":"Invalid Authentication information provided"} """)
    }

    scenario("A user restricted request attempting to use a valid server token is not proxied") {

      val serverToken = "TKN"

      Given("A request with a server token")
      val httpRequest = Http(s"$serviceUrl/api-simulator/userScope1")
        .header(ACCEPT, "application/vnd.hmrc.1.0+json")
        .header(AUTHORIZATION, s"Bearer $serverToken")

      And("An authority does not exist for the token")
      thirdPartyDelegatedAuthority.willNotReturnAnAuthorityForAccessToken(serverToken)

      And("An application exists for the server token")
      thirdPartyApplication.willReturnTheApplicationForServerToken(serverToken, application)

      When("The request is sent to the gateway")
      val httpResponse = invoke(httpRequest)

      Then("The http response is '401' unauthorized")
      assertCodeIs(httpResponse, UNAUTHORIZED)

      And("The response message code is 'INCORRECT_ACCESS_TOKEN_TYPE'")
      assertBodyIs(httpResponse, """ {"code":"INCORRECT_ACCESS_TOKEN_TYPE","message":"The access token type used is not supported when invoking the API"} """)
    }

    scenario("A user restricted request that does not match applications is not proxied") {

      Given("A valid request")
      val httpRequest = Http(s"$serviceUrl/api-simulator/userScope1")
        .header(ACCEPT, "application/vnd.hmrc.1.0+json")
        .header(AUTHORIZATION, "Bearer token")

      And("The access token matches an authority")
      thirdPartyDelegatedAuthority.willReturnTheAuthorityForAccessToken("token", authority)

      And("An application is not found for the delegated authority")
      thirdPartyApplication.willNotFindAnApplicationForClientId("clientId")

      When("The request is sent to the gateway")
      val httpResponse = invoke(httpRequest)

      Then("The http response is '404' not found")
      assertCodeIs(httpResponse, NOT_FOUND)

      And("The response message code is 'SERVER_ERROR'")
      assertBodyIs(httpResponse, """ {"code":"NOT_FOUND","message":"The requested resource could not be found."} """)
    }

    scenario("A user restricted request with invalid subscriptions is not proxied") {

      Given("A valid request")
      val httpRequest = Http(s"$serviceUrl/api-simulator/userScope1")
        .header(ACCEPT, "application/vnd.hmrc.1.0+json")
        .header(AUTHORIZATION, "Bearer token")

      And("The access token matches an authority")
      thirdPartyDelegatedAuthority.willReturnTheAuthorityForAccessToken("token", authority)

      And("An application exists for the delegated authority")
      thirdPartyApplication.willReturnTheApplicationForClientId("clientId", application)

      And("The application is subscribed to the correct API")
      thirdPartyApplication.willNotFindSubscriptionsForApplicationId(applicationId.toString)

      When("The request is sent to the gateway")
      val httpResponse = invoke(httpRequest)

      Then("The http response is '404' not found")
      assertCodeIs(httpResponse, NOT_FOUND)

      And("The response message code is 'SERVER_ERROR'")
      assertBodyIs(httpResponse, """ {"code":"NOT_FOUND","message":"The requested resource could not be found."} """)
    }

    scenario("A user restricted request with invalid scopes is not proxied") {
      Given("A request to an endpoint requiring 'scope2'")
      val httpRequest = Http(s"$serviceUrl/api-simulator/userScope2")
        .header(ACCEPT, "application/vnd.hmrc.1.0+json")
        .header(AUTHORIZATION, s"Bearer $accessToken")

      And("The access token matches an authority with 'scope1'")
      thirdPartyDelegatedAuthority.willReturnTheAuthorityForAccessToken(accessToken, authority)

      And("The access token matches an authority with 'scope1'")
      thirdPartyDelegatedAuthority.willReturnTheAuthorityForAccessToken("token", authority)

      And("An application exists for the delegated authority")
      thirdPartyApplication.willReturnTheApplicationForClientId("clientId", application)

      And("The application is subscribed to the correct API")
      thirdPartyApplication.willReturnTheSubscriptionsForApplicationId(applicationId.toString, correctSubscriptions)

      When("The request is sent to the gateway")
      val httpResponse = invoke(httpRequest)

      Then("The http response is '403' forbidden")
      assertCodeIs(httpResponse, FORBIDDEN)

      And("The response message code is 'INVALID_SCOPE'")
      assertBodyIs(httpResponse, """ {"code":"INVALID_SCOPE","message":"Cannot access the required resource. Ensure this token has all the required scopes."} """)
    }

    scenario("A request passing checks for a user restricted endpoint is proxied") {

      Given("A request to an endpoint requiring 'scope1'")
      val httpRequest = Http(s"$serviceUrl/api-simulator/userScope1")
        .header(ACCEPT, "application/vnd.hmrc.1.0+json")
        .header(AUTHORIZATION, "Bearer token")

      And("The access token matches an authority with 'scope1'")
      thirdPartyDelegatedAuthority.willReturnTheAuthorityForAccessToken("token", authority)

      And("An application exists for the delegated authority")
      thirdPartyApplication.willReturnTheApplicationForClientId("clientId", application)

      And("The application is subscribed to the correct API")
      thirdPartyApplication.willReturnTheSubscriptionsForApplicationId(applicationId.toString, correctSubscriptions)

      When("The request is sent to the gateway")
      val httpResponse = invoke(httpRequest)

      Then("The request is proxied to the microservice")
      assertCodeIs(httpResponse, OK)
      assertBodyIs(httpResponse, apiResponse)
    }
  }

  feature("Application Restricted endpoint") {

    scenario("An application restricted request without an 'authorization' http header is not proxied") {

      Given("A request without an 'authorization' http header")
      val httpRequest = Http(s"$serviceUrl/api-simulator/application").header(ACCEPT, "application/vnd.hmrc.1.0+json")

      When("The request is sent to the gateway")
      val httpResponse = invoke(httpRequest)

      Then("The http response is '401' unauthorized")
      assertCodeIs(httpResponse, UNAUTHORIZED)

      And("The response message code is 'MISSING_CREDENTIALS'")
      assertBodyIs(httpResponse, """ {"code":"MISSING_CREDENTIALS","message":"Authentication information is not provided"} """)
    }

    scenario("An application restricted request with a valid server token is proxied") {

      Given("A request with valid headers")
      val httpRequest = Http(s"$serviceUrl/api-simulator/application")
        .header(ACCEPT, "application/vnd.hmrc.1.0+json")
        .header(AUTHORIZATION, "Bearer token")

      And("The server token matches an application")
      thirdPartyApplication.willReturnTheApplicationForServerToken("token", application)

      And("The application is subscribed to the correct API")
      thirdPartyApplication.willReturnTheSubscriptionsForApplicationId(applicationId.toString, correctSubscriptions)

      When("The request is sent to the gateway")
      val httpResponse = invoke(httpRequest)

      Then("The request is proxied to the microservice")
      assertCodeIs(httpResponse, OK)
      assertBodyIs(httpResponse, apiResponse)
    }

    scenario("An application restricted request that matches a delegated authority is proxied") {

      Given("A request with valid headers")
      val httpRequest = Http(s"$serviceUrl/api-simulator/application")
        .header(ACCEPT, "application/vnd.hmrc.1.0+json")
        .header(AUTHORIZATION, "Bearer token")

      And("The access token does not match applications")
      thirdPartyApplication.willNotFindAnApplicationForServerToken("token")

      And("The access token matches an authority'")
      thirdPartyDelegatedAuthority.willReturnTheAuthorityForAccessToken("token", authority)

      And("An application exists for the delegated authority")
      thirdPartyApplication.willReturnTheApplicationForClientId("clientId", application)

      And("The application is subscribed to the correct API")
      thirdPartyApplication.willReturnTheSubscriptionsForApplicationId(applicationId.toString, correctSubscriptions)

      When("The request is sent to the gateway")
      val httpResponse = invoke(httpRequest)

      Then("The request is proxied to the microservice")
      assertCodeIs(httpResponse, OK)
      assertBodyIs(httpResponse, apiResponse)
    }

    scenario("An application restricted request not matching applications nor authorities is not proxied") {

      Given("A request with valid headers")
      val httpRequest = Http(s"$serviceUrl/api-simulator/application")
        .header(ACCEPT, "application/vnd.hmrc.1.0+json")
        .header(AUTHORIZATION, "Bearer token")

      And("The token does not match any authority")
      thirdPartyDelegatedAuthority.willNotReturnAnAuthorityForAccessToken("token")

      And("The token does not match any application")
      thirdPartyApplication.willNotFindAnApplicationForServerToken("token")

      When("The request is sent to the gateway")
      val httpResponse = invoke(httpRequest)

      Then("The http response is '404' not found")
      assertCodeIs(httpResponse, NOT_FOUND)

      And("The response message code is 'SERVER_ERROR'")
      assertBodyIs(httpResponse, """ {"code":"NOT_FOUND","message":"The requested resource could not be found."} """)
    }

    scenario("An application restricted request not matching applications is not proxied") {

      Given("A request with valid headers")
      val httpRequest = Http(s"$serviceUrl/api-simulator/application")
        .header(ACCEPT, "application/vnd.hmrc.1.0+json")
        .header(AUTHORIZATION, "Bearer token")

      And("The token does not match any application")
      thirdPartyApplication.willNotFindAnApplicationForServerToken("token")

      And("The token matches an authority")
      thirdPartyDelegatedAuthority.willReturnTheAuthorityForAccessToken("token", authority)

      And("The delegated authority does not match any application")
      thirdPartyApplication.willNotFindAnApplicationForClientId("clientId")

      When("The request is sent to the gateway")
      val httpResponse = invoke(httpRequest)

      Then("The http response is '404' not found")
      assertCodeIs(httpResponse, NOT_FOUND)

      And("The response message code is 'SERVER_ERROR'")
      assertBodyIs(httpResponse, """ {"code":"NOT_FOUND","message":"The requested resource could not be found."} """)
    }

    scenario("An application restricted request not matching subscriptions is not proxied") {

      Given("A request with valid headers")
      val httpRequest = Http(s"$serviceUrl/api-simulator/application")
        .header(ACCEPT, "application/vnd.hmrc.1.0+json")
        .header(AUTHORIZATION, "Bearer token")

      And("The server token matches an application")
      thirdPartyApplication.willReturnTheApplicationForServerToken("token", application)

      And("there is an error when fetching the subscriptions")
      thirdPartyApplication.willNotFindSubscriptionsForApplicationId(applicationId.toString)

      When("The request is sent to the gateway")
      val httpResponse = invoke(httpRequest)

      Then("The http response is '404' not found")
      assertCodeIs(httpResponse, NOT_FOUND)

      And("The response message code is 'SERVER_ERROR'")
      assertBodyIs(httpResponse, """ {"code":"NOT_FOUND","message":"The requested resource could not be found."} """)
    }

  }

  feature("Open endpoint") {

    scenario("A request passing checks for an open endpoint is proxied") {

      Given("a request which passes checks for an open endpoint")
      val httpRequest = Http(s"$serviceUrl/api-simulator/open")
        .header(ACCEPT, "application/vnd.hmrc.1.0+json")

      When("the request is sent to the gateway")
      val httpResponse = invoke(httpRequest)

      Then("The request is proxied to the microservice")
      assertCodeIs(httpResponse, OK)
      assertBodyIs(httpResponse, apiResponse)
    }
  }
}
