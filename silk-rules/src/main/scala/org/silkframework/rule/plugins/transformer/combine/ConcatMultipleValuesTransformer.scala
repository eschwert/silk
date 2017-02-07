package org.silkframework.rule.plugins.transformer.combine

import java.util.regex.Pattern

import org.silkframework.rule.input.Transformer
import org.silkframework.runtime.plugin.Plugin

/**
 * Transformer concatenating multiple values using a given glue string. Optionally removes duplicate values.
 * @author Florian Kleedorfer
 *
 */
@Plugin(
  id = "concatMultiValues",
  categories = Array("Combine"),
  label = "ConcatenateMultipleValues",
  description = "Concatenates multiple values received for an input. If applied to multiple inputs, yields at most one value per input. Optionally removes duplicate values."
)
case class ConcatMultipleValuesTransformer(glue: String = "", removeDuplicates:Boolean = false) extends Transformer {
  override def apply(values: Seq[Seq[String]]): Seq[String] = {
    for (strings <- values; if strings.nonEmpty) yield {
      if (removeDuplicates) {
        //glue, split, remove duplicates and glue again to remove more subtle duplicates.
        //e.g. "Albert", "Einstein", "Albert Einstein" -> "Albert Einstein" instead of "Albert Einstein Albert Einstein"
        strings.reduce(_ + glue + _).split(Pattern.quote(glue)).reduce(_ + glue + _)
      } else {
        strings.reduce(_ + glue + _)
      }
    }
  }

}