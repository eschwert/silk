package plugins

import org.silkframework.config.TaskSpec
import org.silkframework.workspace.{Project, ProjectTask, User}

import scala.reflect.ClassTag

/**
 * The context in which a plugin is called.
 *
 * @param project The current project
 * @param task The current task of the project
 * @param path The request url path
 * @tparam T The type of the current task
 */
case class Context[T <: TaskSpec](project: Project, task: ProjectTask[T], path: String)

/**
 * Factory for context objects.
 */
object Context {

  /**
   * Creates a new context.
   *
   * @param projectName The name of the project
   * @param taskName The name of the task
   * @param path The request url path
   * @tparam T The type of the task
   * @return The generated context
   */
  def get[T <: TaskSpec : ClassTag](projectName: String, taskName: String, path: String): Context[T] = {
    val project = User().workspace.project(projectName)
    val task = project.task[T](taskName)

    Context(project, task, path)
  }
}