package org.silkframework.workspace.activity

import org.silkframework.config.TaskSpec
import org.silkframework.runtime.activity.{Activity, HasValue}
import org.silkframework.runtime.plugin.AnyPlugin
import org.silkframework.runtime.serialization.XmlFormat
import org.silkframework.workspace.ProjectTask

import scala.reflect.ClassTag

/**
  * Factory for generating activities that belong to a task.
  *
  * @tparam TaskType The type of the task the generate activities belong to
  * @tparam ActivityType The type of activity that is generated and by which the activity will be identified within the task
  */
abstract class TaskActivityFactory[TaskType <: TaskSpec : ClassTag, ActivityType <: HasValue : ClassTag] extends AnyPlugin
    with (ProjectTask[TaskType] => Activity[ActivityType#ValueType]) {

  /** True, if this activity shall be executed automatically after startup */
  def autoRun: Boolean = false

  /**
    * Generates a new activity for a given task.
    */
  def apply(task: ProjectTask[TaskType]): Activity[ActivityType#ValueType]

  /**
    * Checks, if this factory generates activities for a given task type
    */
  def isTaskType[T: ClassTag]: Boolean = {
    val requestedType = implicitly[ClassTag[T]].runtimeClass
    val taskType = implicitly[ClassTag[TaskType]].runtimeClass
    requestedType.isAssignableFrom(taskType)
  }

  /**
    * Returns the type of generated activities.
    */
  def activityType: Class[_] = implicitly[ClassTag[ActivityType]].runtimeClass
}