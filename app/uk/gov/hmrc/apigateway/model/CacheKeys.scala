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

package uk.gov.hmrc.apigateway.model

object PrimaryCacheKey {
  def apply(key: String, requiredHeader: Option[String] = None, actualHeaders: Map[String, Set[String]] = Map.empty) = {
    requiredHeader match {
      case None => key
      case Some(h)  =>
        val v = actualHeaders.get(h).map(_.toSeq.sorted.mkString(",")).getOrElse("")
        s"$key::$h=$v"
    }
  }
}

object VaryCacheKey {
  def apply(path: String) = {
    s"vary::$path"
  }
}
