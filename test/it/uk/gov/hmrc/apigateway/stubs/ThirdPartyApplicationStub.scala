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

package it.uk.gov.hmrc.apigateway.stubs

import it.uk.gov.hmrc.apigateway.{MockHost, Stub}
import uk.gov.hmrc.apigateway.model.{API, Application}

object ThirdPartyApplicationStub extends Stub with ThirdPartyApplicationStubMappings {

  override val stub = MockHost(22223)

  def willReturnTheApplicationForServerToken(serverToken: String, application: Application) =
    stub.mock.register(returnTheApplicationForServerToken(serverToken, application))

  def willNotFindAnApplicationForServerToken(serverToken: String) =
    stub.mock.register(willNotFindAnyApplicationForServerToken(serverToken))

  def willReturnTheApplicationForClientId(clientId: String, application: Application) =
    stub.mock.register(returnTheApplicationForClientId(clientId, application))

  def willNotFindAnApplicationForClientId(clientId: String) =
    stub.mock.register(willNotFindAnyApplicationForClientId(clientId))

  def willReturnTheSubscriptionsForApplicationId(applicationId: String, subscriptions: Seq[API]) =
    stub.mock.register(returnTheSubscriptionsForApplicationId(applicationId, subscriptions))

  def willNotFindSubscriptionsForApplicationId(applicationId: String) =
    stub.mock.register(willNotFindAnySubscriptionForApplicationId(applicationId))

}
