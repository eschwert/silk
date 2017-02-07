package org.silkframework.rule.execution

import org.silkframework.dataset.{DataSource, EntitySink, TypedProperty}
import org.silkframework.entity.EntitySchema
import org.silkframework.rule.execution.TransformReport.RuleError
import org.silkframework.rule.{DatasetSelection, MappingTarget, TransformRule}
import org.silkframework.runtime.activity.{Activity, ActivityContext}

import scala.util.control.NonFatal

/**
  * Executes a set of transformation rules.
  */
class ExecuteTransform(input: DataSource,
                       selection: DatasetSelection,
                       rules: Seq[TransformRule],
                       outputs: Seq[EntitySink] = Seq.empty,
                       errorOutputs: Seq[EntitySink]) extends Activity[TransformReport] {

  require(rules.count(_.target.isEmpty) <= 1, "Only one rule with empty target property (subject rule) allowed.")

  private val subjectRule = rules.find(_.target.isEmpty)

  private val propertyRules = rules.filter(_.target.isDefined)

  @volatile
  private var isCanceled: Boolean = false

  lazy val entitySchema: EntitySchema = {
    EntitySchema(
      typeUri = selection.typeUri,
      typedPaths = rules.flatMap(_.paths).distinct.map(_.asStringTypedPath).toIndexedSeq,
      filter = selection.restriction
    )
  }

  override val initialValue = Some(TransformReport())

  def run(context: ActivityContext[TransformReport]): Unit = {
    isCanceled = false
    // Retrieve entities
    val entities = input.retrieve(entitySchema)

    // Transform statistics
    val report = new TransformReportBuilder(propertyRules)
    try {
      // Open outputs
      val properties = propertyRules.map(_.target.get)
      for (output <- outputs) output.open(properties map MappingTarget.toTypedProperty)
      val inputProperties = entitySchema.typedPaths.map { p =>
        val uri = p.propertyUri.map(_.uri).getOrElse(p.path.toString)
        TypedProperty(uri, p.valueType)
      }.toIndexedSeq
      for (errorOutput <- errorOutputs) errorOutput.open(inputProperties)

      // Transform all entities and write to outputs
      var count = 0
      for (entity <- entities) {
        report.incrementEntityCounter()
        val uri = subjectRule.flatMap(_ (entity).headOption).getOrElse(entity.uri)
        var success = true
        val values = propertyRules.map { r =>
          try {
            r(entity)
          } catch {
            case NonFatal(ex) =>
              success = false
              report.addError(r, entity, ex)
              Seq()
          }
        }
        if(success) {
          for (output <- outputs) {
            output.writeEntity(uri, values)
          }
        } else {
          report.incrementEntityErrorCounter()
          for (errorOutput <- errorOutputs) {
            errorOutput.writeEntity(uri, entity.values)
          }
        }
        if (isCanceled)
          return
        count += 1
        if (count % 1000 == 0) {
          context.value.update(report.build())
          context.status.updateMessage(s"Executing ($count Entities)")
        }
      }
      context.status.update(s"$count entities written to ${outputs.size} outputs", 1.0)
    } finally {
      // Set final value
      context.value.update(report.build())
      // Close outputs
      for (output <- outputs) output.close()
      for (errorOutput <- errorOutputs) errorOutput.close()
    }
  }

  override def cancelExecution(): Unit = {
    isCanceled = true
  }
}