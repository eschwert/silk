package org.silkframework.rule.plugins.transformer.conditional

import org.silkframework.rule.input.Transformer
import org.silkframework.runtime.plugin.Plugin

@Plugin(
  id = "ifExists",
  label = "if exists",
  categories = Array("Conditional"),
  description = "Accepts two or three inputs. If the first input provides a value, the second input is forwarded. Otherwise, the third input is forwarded (if present)."
)
case class IfExists() extends Transformer {
  override def apply(values: Seq[Seq[String]]): Seq[String] = {
    require(values.size >= 2, "The ifExists transformation requires at least two inputs")
    if(values(0).nonEmpty)
      values(1)
    else
      if(values.size >= 3) values(2) else Seq.empty
  }
}