package org.silkframework.workspace.activity.dataset

import org.silkframework.config.Task
import org.silkframework.dataset.Dataset
import org.silkframework.runtime.activity.{Activity, ActivityContext}

/**
 * Holds the most frequent types.
 */
class TypesCache(dataset: Task[Dataset]) extends Activity[Types] {

  override def name = s"Types cache ${dataset.id}"

  override def initialValue = Some(Types.empty)

  override def run(context: ActivityContext[Types]): Unit = {
    val dataSource = dataset.source
    val types = Types(dataSource.retrieveTypes().toSeq)
    context.value() = types
  }
}