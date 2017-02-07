package org.silkframework.dataset

import org.silkframework.entity.{TypedPath, ValueType}
import org.silkframework.runtime.validation.ValidationException

/**
 * An entity sink implements methods to write entities, e.g. the result of a transformation task.
 */
trait EntitySink extends DataSink {
  /**
   * Initializes this writer.
   *
   * @param paths The list of paths of the entities to be written.
   */
  def open(paths: Seq[TypedPath]): Unit

  /**
   * Writes a new entity.
   *
   * @param subject The subject URI of the entity.
   * @param values The list of values of the entity. For each property that has been provided
   *               when opening this writer, it must contain a set of values.
   */
  def writeEntity(subject: String, values: Seq[Seq[String]]): Unit
}

/**
  * An entity sink that writes flat entities, i.e., with paths of length 1.
  */
trait FlatEntitySink extends EntitySink {

  /**
    * Initializes this writer.
    *
    * @param paths The list of paths of the entities to be written.
    */
  final override def open(paths: Seq[TypedPath]): Unit = {
    val properties =
      for(path <- paths) yield path.propertyUri match {
        case Some(property) => TypedProperty(property.toString, path.valueType)
        case None => throw new ValidationException("Cannot write hierarchical entities to an " + getClass.getSimpleName)
    }
    openFlat(properties)
  }

  /**
    * Initializes this writer using a flat schema.
    *
    * @param properties The list of properties of the entities to be written.
    */
  def openFlat(properties: Seq[TypedProperty]): Unit

}

case class TypedProperty(propertyUri: String, valueType: ValueType)
