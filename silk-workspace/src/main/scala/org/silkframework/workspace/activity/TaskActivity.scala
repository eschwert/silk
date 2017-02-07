package org.silkframework.workspace.activity

import java.lang.reflect.{ParameterizedType, Type}

import org.silkframework.config.TaskSpec
import org.silkframework.runtime.activity.{Activity, HasValue}
import org.silkframework.runtime.plugin.PluginDescription
import org.silkframework.workspace.ProjectTask

import scala.reflect.ClassTag

/**
  * Holds an activity that is part of an task.
  *
  * @param task           The task this activity belongs to.
  * @param initialFactory The initial activity factory for generating the activity.
  * @tparam DataType The type of the task.
  */
class TaskActivity[DataType <: TaskSpec : ClassTag, ActivityType <: HasValue : ClassTag](val task: ProjectTask[DataType],
                                                                                         initialFactory: TaskActivityFactory[DataType, ActivityType])
    extends WorkspaceActivity {
  final val activityClassName = classOf[Activity[_]].getName

  @volatile
  private var currentControl = Activity(initialFactory(task))

  @volatile
  private var currentFactory = initialFactory

  override def name = currentFactory.plugin.id

  override def project = task.project

  override def taskOption = Option(task)

  def value = currentControl.value()

  override def status = currentControl.status()

  def autoRun = currentFactory.autoRun

  def control = currentControl

  def factory = currentFactory

  def config: Map[String, String] = PluginDescription(currentFactory.getClass).parameterValues(currentFactory)

  def reset() = {
    currentControl.cancel()
    recreateControl()
  }

  def update(config: Map[String, String]) = {
    implicit val prefixes = task.project.config.prefixes
    implicit val resources = task.project.resources
    currentFactory = PluginDescription(currentFactory.getClass)(config)
    recreateControl()
  }

  private def recreateControl() = {
    val oldControl = currentControl
    currentControl = Activity(currentFactory(task))
    // Keep subscribers
    for (subscriber <- oldControl.status.subscribers) {
      currentControl.status.onUpdate(subscriber)
    }
    for (subscriber <- oldControl.value.subscribers) {
      currentControl.value.onUpdate(subscriber)
    }
  }

  def activityType: Class[_] = currentFactory.activityType

  /**
    * Retrieves the value type of the activity.
    */
  def valueType: Class[_] = {
    val activityInterface = {
      val at = activityType
      val gi = getAllInterfacesRecursively(at, activityClassName)
      gi.find(_.getTypeName.startsWith(activityClassName)) match {
        case Some(activityTrait) =>
          activityTrait.asInstanceOf[ParameterizedType]
        case None =>
          throw new Exception("Not able to get value type of activity " + at.getName)
      }
    }
    val valueType = activityInterface.getActualTypeArguments.apply(0)
    val valueClass = valueType match {
      case pt: ParameterizedType => pt.getRawType.asInstanceOf[Class[_]]
      case t: Type => t.asInstanceOf[Class[_]]
    }
    valueClass
  }

  private def getAllInterfacesRecursively(clazz: Type, stopAtClassPrefix: String): List[Type] = {
    if(clazz.getTypeName.startsWith(stopAtClassPrefix)) {
      List(clazz)
    } else {
      val recursiveTypes: List[Type] = clazz match {
        case c: Class[_] =>
          val genericInterfaces = c.getGenericInterfaces.toList
          genericInterfaces ++ genericInterfaces.flatMap(getAllInterfacesRecursively(_, stopAtClassPrefix))
        case pt: ParameterizedType =>
          getAllInterfacesRecursively(pt.getRawType, stopAtClassPrefix)
        case t: Type =>
          List()
      }
      clazz :: recursiveTypes
    }
  }
}
