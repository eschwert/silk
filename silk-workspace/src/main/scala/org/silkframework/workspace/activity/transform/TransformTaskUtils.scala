package org.silkframework.workspace.activity.transform

import org.silkframework.dataset.{DataSource, Dataset, DatasetTask}
import org.silkframework.rule.TransformSpec
import org.silkframework.workspace.ProjectTask

/**
  * Adds additional methods to transform tasks.
  */
object TransformTaskUtils {

  implicit class TransformTask(task: ProjectTask[TransformSpec]) {

    /**
      * Retrieves the data source for this transform task.
      */
    def dataSource: DataSource = {
      task.project.task[Dataset](task.data.selection.inputId).data.source
    }

    /**
      * Retrieves all entity sinks for this transform task.
      */
    def entitySinks = {
      task.data.outputs.flatMap(o => task.project.taskOption[Dataset](o)).map(_.data.entitySink)
    }

    /**
      * Retrieves all error entity sinks for this transform task.
      */
    def errorEntitySinks = {
      task.data.errorOutputs.flatMap(o => task.project.taskOption[Dataset](o)).map(_.data.entitySink)
    }
  }

}
