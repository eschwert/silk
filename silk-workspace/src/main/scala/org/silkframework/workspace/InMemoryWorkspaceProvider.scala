package org.silkframework.workspace

import org.silkframework.runtime.plugin.Plugin
import org.silkframework.runtime.resource.{InMemoryResourceManager, ResourceManager}
import org.silkframework.util.Identifier
import scala.reflect.ClassTag

@Plugin(
  id = "inMemory",
  label = "In-memory workspace",
  description = "Workspace provider that holds all projects in memory. All contents will be gone on restart."
)
case class InMemoryWorkspaceProvider() extends WorkspaceProvider with RefreshableWorkspaceProvider {

  protected var projects = Map[Identifier, InMemoryProject]()

  /**
    * Reads all projects from the workspace.
    */
  override def readProjects(): Seq[ProjectConfig] = projects.values.map(_.config).toSeq

  /**
    * Adds/Updates a project.
    */
  override def putProject(project: ProjectConfig): Unit = {
    projects += ((project.id, new InMemoryProject(project.copy(projectResourceUriOpt = Some(project.resourceUriOrElseDefaultUri)))))
  }

  /**
    * Deletes a project.
    */
  override def deleteProject(name: Identifier): Unit = {
    projects -= name
  }

  /**
    * Retrieves the project cache folder.
    */
  override def projectCache(name: Identifier): ResourceManager = projects(name).cache

  /**
    * Adds/Updates a task in a project.
    */
  override def putTask[T: ClassTag](project: Identifier, task: Identifier, data: T): Unit = {
    projects(project).tasks += ((task, data))
  }

  /**
    * Reads all tasks of a specific type from a project.
    */
  override def readTasks[T: ClassTag](project: Identifier, projectResources: ResourceManager): Seq[(Identifier, T)] = {
    for((id, task: T) <- projects(project).tasks.toSeq) yield (id, task)
  }

  /**
    * Deletes a task from a project.
    */
  override def deleteTask[T: ClassTag](project: Identifier, task: Identifier): Unit = {
    projects(project).tasks -= task
  }

  /**
    * No refresh needed.
    */
  override def refresh(): Unit = {}

  protected class InMemoryProject(val config: ProjectConfig) {

    var tasks: Map[Identifier, Any] = Map.empty

    val resources = new InMemoryResourceManager

    val cache = new InMemoryResourceManager

  }
}
