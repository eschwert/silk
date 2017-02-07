/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.silkframework.rule.plugins.transformer.date

import org.scalatest.{FlatSpec, Matchers}
import org.silkframework.test.PluginTest

class ParseDataTransformerTest extends PluginTest {

  val transformer = ParseDateTransformer("dd.MM.yyyy")

  "ParseDataTransformer" should "parse dates" in {
    transformer(Seq(Seq("03.04.2015"))) should equal(Seq("2015-04-03"))
    transformer(Seq(Seq("3.4.2015"))) should equal(Seq("2015-04-03"))
    transformer(Seq(Seq("03.4.2015"))) should equal(Seq("2015-04-03"))
  }

  override def pluginObject = ParseDateTransformer("dd.MM.yyyy")
}
