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

package org.silkframework.rule.execution

import java.util.Locale
import java.util.logging.{Level, Logger}

import org.silkframework.entity.{Link, Path}
import org.silkframework.rule.evaluation.ReferenceLinksReader
import org.silkframework.rule.execution.methods._
import org.silkframework.rule.plugins.transformer.linguistic.{MetaphoneTransformer, NysiisTransformer, SoundexTransformer}
import org.silkframework.rule.{LinkingConfig, RuntimeLinkingConfig}
import org.silkframework.runtime.activity.Activity
import org.silkframework.runtime.resource.ClasspathResourceLoader
import org.silkframework.runtime.serialization.{ReadContext, XmlSerialization}

import scala.io.Source
import scala.xml.XML

/**
 * This test evaluates the GenerateLinks Activity with different execution methods.
 */
object GenerateLinksTest {

  Locale.setDefault(Locale.ENGLISH)

  private val log = Logger.getLogger(getClass.getName)

  private val resourceLoader = new ClasspathResourceLoader("names")

  /** Directory of the data set */
  private val dataset = Dataset("Names", "config.xml", "links.nt")

  private val sourceKey = Path.parse("?a/<label>")
  private val targetKey = Path.parse("?b/<label>")

  private val tests =
    Test("Full", Full()) ::
    Test("Blocking (Soundex)", Blocking(sourceKey, targetKey, q = 100, transformers = SoundexTransformer(refined = false) :: Nil)) ::
    Test("Blocking (NYSIIS)", Blocking(sourceKey, targetKey, q = 100, transformers = NysiisTransformer(refined = false) :: Nil)) ::
    Test("Blocking (Metaphone)", Blocking(sourceKey, targetKey, q = 100, transformers = MetaphoneTransformer() :: Nil)) ::
    Test("Sorted Blocks (10%)", SortedBlocks(sourceKey, targetKey, overlap = 0.1)) ::
    Test("Sorted Blocks (50%)", SortedBlocks(sourceKey, targetKey, overlap = 0.5)) ::
    Test("StringMap (0.1)", StringMap(sourceKey, targetKey, distThreshold = 2, thresholdPercentage = 0.1)) ::
    Test("StringMap (0.5)", StringMap(sourceKey, targetKey, distThreshold = 2, thresholdPercentage = 0.5)) ::
    Test("Q-Grams", QGrams(sourceKey, targetKey, q = 2, t = 0.7)) ::
    Test("Q-Grams", QGrams(sourceKey, targetKey, q = 2, t = 0.8)) ::
    Test("Q-Grams", QGrams(sourceKey, targetKey, q = 2, t = 0.9)) ::
    Test("MultiBlock", MultiBlock()) ::
    Nil

  def main(args: Array[String]) {
    val results = for(test <- tests) yield test.run

    println("Results:")
    println(Result.latexHeader)
    results.map(_.toLatex).foreach(println)
  }

  /**
   * Specifies an evaluation data set.
   *
   * @param name The name of the data set
   * @param configFile The path of the linking config file.
   * @param referenceLinksFile The path of the reference links.
   */
  private case class Dataset(name: String, configFile: String, referenceLinksFile: String) {
    lazy val config: LinkingConfig = {
      implicit val readContext = ReadContext()
      val xml = XML.load(resourceLoader.get(configFile).load)
      XmlSerialization.fromXml[LinkingConfig](xml)
    }

    lazy val referenceLinks: Set[Link] = {
      val stream = resourceLoader.get(referenceLinksFile).load
      ReferenceLinksReader.readNTriples(Source.fromInputStream(stream)).positive
    }
  }

  /**
   * Specifies a test.
   *
   * @param name The name of this configuration
   * @param executionMethod The execution method to be evaluated
   */
  private case class Test(name: String, executionMethod: ExecutionMethod) {

    /** Runs this test and returns the evaluation result */
    def run: Result = {
      log.info("Running " + name + " test...")

      val fullLinks = dataset.referenceLinks
      val foundLinks = runIndexing()
      val correctLinks = foundLinks intersect fullLinks
      val missedLinks = fullLinks -- foundLinks

      // Run the test three times
      val runtimes = for(i <- 0 until 3) yield runComplete()

      Result(
        name = name,
        comparisonPairs = foundLinks.size,
        pairsCompleteness = 1.0 - missedLinks.size.toDouble / fullLinks.size,
        pairsQuality = correctLinks.size.toDouble / foundLinks.size,
        runtime = runtimes.min
      )
    }

    /** Runs only the indexing and returns the links found by the index. */
    private def runIndexing() = {
      val links = run(RuntimeLinkingConfig(
        executionMethod = executionMethod,
        indexingOnly = true,
        useFileCache = false,
        logLevel = Level.FINE
      ))

      log.info("Found " + links.size + " links in index.")
      links
    }

    /** Runs the complete matching task and returns the runtime */
    private def runComplete() = {
      val startTime = System.currentTimeMillis

      run(RuntimeLinkingConfig(
        executionMethod = executionMethod,
        indexingOnly = false,
        useFileCache = false,
        logLevel = Level.FINE
      ))

      val elapsedTime = (System.currentTimeMillis - startTime).toDouble / 1000.0
      log.info("Executed in " + elapsedTime + "s.")
      elapsedTime
    }

    private def run(runtimeConfig: RuntimeLinkingConfig): Set[Link] = {
      val config = dataset.config
      val linkTask = config.linkSpecs.head

      // Execute Matching
      val activity =
        GenerateLinks.fromSources(
          id = linkTask.id,
          datasets = config.sources,
          linkSpec = linkTask.data,
          runtimeConfig = runtimeConfig
        )

      val links = Activity(activity).startBlockingAndGetValue().links
      links.toSet
    }
  }

  /**
   * The result of executing a test.
   *
   * @param name The name of the executed test
   * @param pairsCompleteness The pairs completeness
   * @param pairsQuality The pairs quality
   * @param runtime The runtime in s
   */
  private case class Result(name: String, comparisonPairs: Long, pairsCompleteness: Double, pairsQuality: Double, runtime: Double) {
    override def toString =
      s"$name: Comparison Pairs = $comparisonPairsF  Pairs Completeness = $pairsCompletenessF Pairs Quality = $pairsQualityF Runtime = $runtimeF"

    def toLatex = s"$name & $comparisonPairsF & $pairsCompletenessF & $pairsQualityF & $runtimeF \\\\".replace("%", "\\%")

    def comparisonPairsF = f"$comparisonPairs%,d"

    def pairsCompletenessF = f"${pairsCompleteness * 100.0}%.2f%%"

    def pairsQualityF = f"${pairsQuality * 100.0}%.2f%%"

    def runtimeF = f"$runtime%.1fs"
  }

  private object Result {
    def latexHeader = "Method & Comparison Pairs & Pairs Completeness & Pairs Quality & Runtime \\\\"
  }
}