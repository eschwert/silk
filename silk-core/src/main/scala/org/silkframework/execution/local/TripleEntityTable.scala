package org.silkframework.execution.local

import org.silkframework.config.{SilkVocab, Task, TaskSpec}
import org.silkframework.entity._
import org.silkframework.util.Uri

/**
  * Holds RDF triples.
  */
case class TripleEntityTable(entities: Traversable[Entity], task: Task[TaskSpec]) extends EntityTable {
  override def entitySchema: EntitySchema = TripleEntitySchema.schema
}

object TripleEntitySchema {
  final val schema = EntitySchema(
    typeUri = Uri(SilkVocab.TripleSchemaType),
    typedPaths = IndexedSeq(
      TypedPath(Path(SilkVocab.tripleSubject), UriValueType),
      TypedPath(Path(SilkVocab.triplePredicate), UriValueType),
      TypedPath(Path(SilkVocab.tripleObject), StringValueType),
      TypedPath(Path(SilkVocab.tripleObjectValueType), StringValueType)
    )
  )
}