package org.silkframework.workspace.activity.linking

import org.silkframework.dataset.{DataSource, Dataset}
import org.silkframework.rule.{LinkSpec, TransformSpec, TransformedDataSource}
import org.silkframework.util.{DPair, Identifier}
import org.silkframework.workspace.ProjectTask

/**
  * Adds additional methods to linking tasks.
  */
object LinkingTaskUtils {

  implicit class LinkingTask(task: ProjectTask[LinkSpec]) {

    /**
      * Retrieves both data sources for this linking task.
      */
    def dataSources: DPair[DataSource] = {
      task.data.dataSelections.map(ds => dataSource(ds.inputId))
    }

    /**
      * Retrieves a specific data source for this linking task.
      */
    def dataSource(sourceId: Identifier): DataSource = {
      task.project.taskOption[TransformSpec](sourceId) match {
        case Some(transformTask) =>
          val source = task.project.task[Dataset](transformTask.data.selection.inputId).data.source
          new TransformedDataSource(source, transformTask.data)
        case None =>
          task.project.task[Dataset](sourceId).data.source
      }
    }

    /**
      * Retrieves all link sinks for this linking task.
      */
    def linkSinks = {
      task.data.outputs.flatMap(o => task.project.taskOption[Dataset](o)).map(_.data.linkSink)
    }
  }

}
