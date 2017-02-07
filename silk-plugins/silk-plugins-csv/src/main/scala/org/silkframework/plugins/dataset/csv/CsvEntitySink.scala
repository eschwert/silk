package org.silkframework.plugins.dataset.csv

import org.silkframework.dataset.{EntitySink, FlatEntitySink}
import org.silkframework.runtime.resource.{Resource, WritableResource}

/**
 * Created by andreas on 12/11/15.
 */
class CsvEntitySink(file: WritableResource, settings: CsvSettings) extends CsvSink(file, settings) with FlatEntitySink {

  override def writeEntity(subject: String, values: Seq[Seq[String]]) {
    write(values.map(_.mkString(settings.arraySeparator.getOrElse(' ').toString)))
  }
}
