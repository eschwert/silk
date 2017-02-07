package org.silkframework.execution.local

import org.silkframework.config.{Task, TaskSpec}
import org.silkframework.entity.{Entity, EntitySchema}

case class GenericEntityTable(entities: Traversable[Entity], entitySchema: EntitySchema, task: Task[TaskSpec]) extends EntityTable
