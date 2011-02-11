package de.fuberlin.wiwiss.silk.workbench.workspace

import java.io.File
import modules.linking.LinkingTask
import java.net.URI
import modules.source.SourceTask

/**
 * Dummy user as there is no user management yet.
 */
trait User
{
  private var currentSourceTask : Option[SourceTask] = None

  private var currentLinkingTask : Option[LinkingTask] = None

  /**
   * The current workspace of this user
   */
  def workspace : Workspace

  // TODO - to be remove - used as fake project selection for the workbench-model package
  var project = workspace.projects.toSeq.last

  /**
   * True, if a source task is open at the moment.
   */
  def sourceTaskOpen = currentSourceTask.isDefined

  /**
   * The current source task of this user.
   *
   * @throws java.util.NoSuchElementException If no source task is open
   */
  def sourceTask = currentSourceTask.getOrElse(throw new NoSuchElementException("No active source task"))

  /**
   * Sets the current source task of this user.
   */
  def sourceTask_=(task : SourceTask) =
  {
    currentSourceTask = Some(task)
  }

  /**
   * True, if a linking task is open at the moment.
   */
  def linkingTaskOpen = currentLinkingTask.isDefined

  /**
   *  The current linking tasks of this user.
   *
   * @throws java.util.NoSuchElementException If no linking task is open
   */
  //TODO document exception
  def linkingTask = currentLinkingTask.getOrElse(throw new NoSuchElementException("No active linking task"))

  /**
   * Sets the current linking task of this user.
   */
  def linkingTask_=(task : LinkingTask) =
  {
    currentLinkingTask = Some(task)
  }
}

object User
{
  private val user = new FileUser()

  /**
   *  Retrieves the current user.
   */
  def apply() = user
}