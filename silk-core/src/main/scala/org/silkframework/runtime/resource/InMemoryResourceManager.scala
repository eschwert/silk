package org.silkframework.runtime.resource

import java.io._

/**
  * A resource manager which holds all data in memory
  */
case class InMemoryResourceManager() extends InMemoryResourceManagerBase()

/**
  * Base class of [[InMemoryResourceManager]] for avoiding leaking implementation details.
  */
class InMemoryResourceManagerBase(val basePath: String = "", parentMgr: Option[InMemoryResourceManagerBase] = None) extends ResourceManager {

  /** Holds all resources at this path. */
  @volatile private var resources = Map[String, Array[Byte]]()

  /** Hold all non-empty child resource managers. */
  @volatile private var children = Map[String, InMemoryResourceManagerBase]()

  /**
    * Retrieves a name resource.
    *
    * @param name      The name of the resource.
    * @param mustExist If true, an ResourceNotFoundException is thrown if the resource does not exist
    * @return The resource.
    * @throws ResourceNotFoundException If no resource with the given name has been found.
    */
  override def get(name: String, mustExist: Boolean): WritableResource = {
    val path = basePath + "/" + name

    resources.get(name) match {
      case Some(data) => new InMemoryResource(name, path)
      case None if !mustExist => new InMemoryResource(name, path)
      case None if mustExist => throw new ResourceNotFoundException(s"Resource $name not found in path $basePath")
    }
  }

  var label = "no name"

  /**
    * Lists all available resources.
    */
  override def list: List[String] = resources.keys.toList

  override def listChildren: List[String] = children.keys.toList

  override def child(name: String): ResourceManager = {
    children.get(name) match {
      case Some(childMgr) => childMgr
      case None =>
        val childMgr = new InMemoryResourceManagerBase(basePath + "/" + name, Some(this))
        children += ((name, childMgr))
        childMgr
    }
  }

  override def parent: Option[ResourceManager] = parentMgr

  override def delete(name: String): Unit = {
    resources -= name
    children -= name
  }

  /**
    * A resource that is held in memory.
    */
  private class InMemoryResource(val name: String, val path: String) extends WritableResource {

    override def exists: Boolean = resources.contains(name)

    override def size = {
      resources.get(name) match {
        case Some(data) => Some(data.length.toLong)
        case None => None
      }
    }

    override def modificationTime = None

    override def load: InputStream = {
      resources.get(name) match {
        case Some(data) => new ByteArrayInputStream(data)
        case None => new ByteArrayInputStream(Array.empty)
      }
    }

    override def write(write: (OutputStream) => Unit): Unit = {
      val outputStream = new ByteArrayOutputStream()
      write(outputStream)
      resources += ((name, outputStream.toByteArray))
    }

    /**
      * Writes raw bytes.
      * Overridden for performance.
      */
    override def write(bytes: Array[Byte]): Unit = {
      resources += ((name, bytes))
    }
  }

}