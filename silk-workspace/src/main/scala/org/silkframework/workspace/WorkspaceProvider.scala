package org.silkframework.workspace

import java.io.{InputStream, OutputStream}

import org.silkframework.config.TaskSpec
import org.silkframework.runtime.resource.{EmptyResourceManager, ResourceLoader, ResourceManager}
import org.silkframework.util.Identifier
import org.silkframework.workspace.resources.ResourceRepository

import scala.reflect.ClassTag

trait WorkspaceProvider {

  /**
   * Reads all projects from the workspace.
   */
  def readProjects(): Seq[ProjectConfig]

  /**
   * Adds/Updates a project.
   */
  def putProject(projectConfig: ProjectConfig): Unit

  /**
   * Deletes a project.
   */
  def deleteProject(name: Identifier): Unit

  /**
   * Retrieves the project cache folder.
   */
  def projectCache(name: Identifier): ResourceManager

  /**
   * Reads all tasks of a specific type from a project.
   */
  def readTasks[T <: TaskSpec : ClassTag](project: Identifier, projectResources: ResourceManager): Seq[(Identifier, T)]

  /**
   * Adds/Updates a task in a project.
   */
  def putTask[T <: TaskSpec : ClassTag](project: Identifier, task: Identifier, data: T): Unit

  /**
   * Deletes a task from a project.
   */
  def deleteTask[T <: TaskSpec : ClassTag](project: Identifier, task: Identifier): Unit
}
