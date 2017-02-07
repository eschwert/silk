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

package org.silkframework.plugins.dataset.rdf.sparql

import org.silkframework.config.Prefixes
import org.silkframework.entity._

/**
 * Builds a SPARQL pattern from Paths.
 */
object SparqlPathBuilder {
  /**
   * Builds a SPARQL pattern from Paths.
   *
   * @param paths The paths
   * @param subject The subject e.g. ?s or <uri>
   * @param valuesPrefix The value of every path will be bound to a variable of the form: valuesPrefix{path.id}
   */
  def apply(paths: Seq[Path], subject: String = "?s", valuesPrefix: String = "?v"): String = {
    val vars = new Vars(subject, valuesPrefix)
    paths.zipWithIndex.map {
      case (path, index) => buildPath(path, index, vars)
    }.mkString
  }

  /**
   * Builds a SPARQL pattern from a single Path.
   */
  private def buildPath(path: Path, index: Int, vars: Vars): String = {
    "OPTIONAL {\n" +
        buildOperators(vars.subject, path.operators, vars).replace(vars.curTempVar, vars.newValueVar(path, index)) +
    "}\n"
  }

  /**
   * Builds a SPARQL pattern from a list of operators.
   */
  private def buildOperators(subject: String, operators: List[PathOperator], vars: Vars): String = {
    if (operators.isEmpty) return ""

    implicit val prefixes = Prefixes.empty

    val operatorSparql = operators.head match {
      case ForwardOperator(property) => subject + " <" + property.uri + "> " + vars.newTempVar + " .\n"
      case BackwardOperator(property) => vars.newTempVar + " <" + property.uri + "> " + subject + " .\n"
      case LanguageFilter(op, lang) => "FILTER(lang(" + subject + ") " + op + " '" + lang + "') . \n"
      case PropertyFilter(property, op, value) => subject + " <" + property.uri + "> " + vars.newFilterVar + " .\n" +
        "FILTER(" + vars.curFilterVar + " " + op + " " + value + ") . \n"
    }

    if (operators.tail.nonEmpty) {
      operatorSparql + buildOperators(vars.curTempVar, operators.tail, vars)
    } else {
      operatorSparql
    }
  }

  /**
   * Holds all variables used during the construction of a SPARQL pattern.
   */
  private class Vars(val subject: String = "?s", val valuesPrefix: String = "?v") {
    private val TempVarPrefix = "?t"

    private val FilterVarPrefix = "?f"

    private var tempVarIndex = 0

    private var filterVarIndex = 0

    def newTempVar: String = {
      tempVarIndex += 1; TempVarPrefix + tempVarIndex
    }

    def curTempVar: String = TempVarPrefix + tempVarIndex

    def newFilterVar: String = {
      filterVarIndex += 1; FilterVarPrefix + filterVarIndex
    }

    def curFilterVar: String = FilterVarPrefix + filterVarIndex

    def newValueVar(path: Path, index: Int): String = valuesPrefix + index
  }

}