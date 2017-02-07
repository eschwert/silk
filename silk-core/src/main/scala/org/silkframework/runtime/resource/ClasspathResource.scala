package org.silkframework.runtime.resource

/**
  * A resource in the classpath.
  *
  * @param path The path of the resource, e.g., "org/silkframework/resource.txt"
  */
class ClasspathResource(val path: String) extends Resource {

  val name: String = path.split(',').last

  def exists = {
    getClass.getClassLoader.getResourceAsStream(path) != null
  }

  def size = None

  def modificationTime = None

  override def load = {
    getClass.getClassLoader.getResourceAsStream(path)
  }
}
