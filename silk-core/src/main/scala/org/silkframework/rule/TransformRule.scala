package org.silkframework.rule

import org.silkframework.config.Prefixes
import org.silkframework.entity.{Entity, Path}
import org.silkframework.plugins.transformer.combine.ConcatTransformer
import org.silkframework.plugins.transformer.value.ConstantTransformer
import org.silkframework.rule.input.{Input, PathInput, TransformInput}
import org.silkframework.runtime.resource.ResourceManager
import org.silkframework.runtime.serialization.{Serialization, ValidatingXMLReader, XmlFormat}
import org.silkframework.util._

import scala.xml.Node

/**
 * A transformation rule.
 * A transformations rule generates property values from based on an arbitrary operator tree consisting of property paths and transformations.
 * Sub classes are defined for special cases, such as direct mappings.
 */
sealed trait TransformRule {

  /** The name of this rule. */
  def name: Identifier

  /** The input operator tree. */
  def operator: Input

  /** The target property URI. */
  def target: Option[Uri]

  /** The child rules for this rule. */
  def childRules: Seq[TransformRule] = Seq.empty

  /**
   * Generates the transformed values.
   *
   * @param entity The source entity.
   * @return The transformed values.
   */
  def apply(entity: Entity): Seq[String] = {
    operator(entity)
  }

  /**
   * Collects all paths in this rule.
   */
  def paths: Set[Path] = {
    def collectPaths(param: Input): Set[Path] = param match {
      case p: PathInput if p.path.operators.isEmpty => Set()
      case p: PathInput => Set(p.path)
      case p: TransformInput => p.inputs.flatMap(collectPaths).toSet
    }

    collectPaths(operator)
  }
}

/**
 * A direct mapping between two properties.
 *
 * @param name The name of this mapping
 * @param sourcePath The source path
 * @param targetProperty The target property
 */
case class DirectMapping(name: Identifier = "transform", sourcePath: Path = Path(Nil), targetProperty: Uri = "http://www.w3.org/2000/01/rdf-schema#label") extends TransformRule {

  override val operator = PathInput(path = sourcePath)

  override val target = Some(targetProperty)
}

/**
 * Assigns a new URI to each mapped entity.
 *
 * @param name The name of this mapping
 * @param pattern A template pattern for generating the URIs based on the entity properties
 */
case class UriMapping(name: Identifier = "uri", pattern: String = "http://example.org/{ID}") extends TransformRule {

  override val operator = {
    val inputs =
      for ((str, i) <- pattern.split("[\\{\\}]").toList.zipWithIndex) yield {
        if (i % 2 == 0)
          TransformInput(transformer = ConstantTransformer(str))
        else
          PathInput(path = Path.parse(str))
      }
    TransformInput(transformer = ConcatTransformer(""), inputs = inputs)
  }

  override val target = None
}

/**
 * Generates a link to another entity.
 *
 * @param name The name of this mapping
 * @param pattern A template pattern for generating the URIs based on the entity properties
 */
case class ObjectMapping(name: Identifier = "object", pattern: String = "http://example.org/{ID}", targetProperty: Uri = "http://www.w3.org/2002/07/owl#sameAs") extends TransformRule {

  override val operator = {
    val inputs =
      for ((str, i) <- pattern.split("[\\{\\}]").toList.zipWithIndex) yield {
        if (i % 2 == 0)
          TransformInput(transformer = ConstantTransformer(str))
        else
          PathInput(path = Path.parse(str))
      }
    TransformInput(transformer = ConcatTransformer(""), inputs = inputs)
  }

  override val target = Some(targetProperty)
}

/**
 * A type mapping, which assigns a type to each entitity.
 *
 * @param name The name of this mapping
 * @param typeUri The type URI.
 */
case class TypeMapping(name: Identifier = "type", typeUri: Uri = "http://www.w3.org/2002/07/owl#Thing") extends TransformRule {

  override val operator = TransformInput(transformer = ConstantTransformer(typeUri.uri))

  override val target = Some(Uri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"))
}

/**
 * A complex mapping, which generates property values from based on an arbitrary operator tree consisting of property paths and transformations.
 *
 * @param name The name of this mapping
 * @param operator The input operator tree
 * @param target The target property URI
 */
case class ComplexMapping(name: Identifier = "mapping", operator: Input, target: Option[Uri] = None) extends TransformRule

/**
  * A hierarchical mapping.
  *
  * Generates child entities that are connected to the parent entity using the targetProperty.
  * The properties of the child entities are mapped by the child mappings.
  *
  * @param name The name of this mapping.
  * @param relativePath All child mappings are relative to this path.
  * @param targetProperty The property that is used to attach the child entities.
  * @param childRules The child rules.
  */
case class HierarchicalMapping(name: Identifier = "mapping", relativePath: Path = Path(Nil), targetProperty: Uri = "hasChild",
                        override val childRules: Seq[TransformRule]) extends TransformRule {

  override val operator = PathInput(path = relativePath)

  override val target = Some(targetProperty)

}

/**
 * Creates new transform rules.
 */
object TransformRule {

  /**
   * XML serialization format.
   */
  implicit object TransformRuleFormat extends XmlFormat[TransformRule] {

    import Serialization._

    def read(node: Node)(implicit prefixes: Prefixes, resources: ResourceManager): TransformRule = {
      ValidatingXMLReader.validate(node, "org/silkframework/LinkSpecificationLanguage.xsd")
      val target = (node \ "@targetProperty").text
      val complex =
        ComplexMapping(
          name = (node \ "@name").text,
          operator = fromXml[Input]((node \ "_").head),
          target = if(target.isEmpty) None else Some(Uri.parse(target, prefixes))
        )
      simplify(complex)
    }

    def write(value: TransformRule)(implicit prefixes: Prefixes): Node = {
      <TransformRule name={value.name} targetProperty={value.target.map(_.uri).getOrElse("")}>
        { toXml(value.operator) }
      </TransformRule>
    }
  }

  /**
   * Tries to express a complex mapping as a basic mapping, such as a direct mapping.
   */
  def simplify(complexMapping: ComplexMapping): TransformRule = complexMapping match {
    // Direct Mapping
    case ComplexMapping(id, PathInput(_, path), Some(target)) =>
      DirectMapping(id, path, target)
    // URI Mapping
    case ComplexMapping(id, TransformInput(_, ConcatTransformer(""), inputs), None) if isPattern(inputs) =>
      UriMapping(id, buildPattern(inputs))
    // Object Mapping
    case ComplexMapping(id, TransformInput(_, ConcatTransformer(""), inputs), Some(target)) if isPattern(inputs) =>
      ObjectMapping(id, buildPattern(inputs), target)
    // Type Mapping
    case ComplexMapping(id, TransformInput(_, ConstantTransformer(typeUri), Nil), Some(Uri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"))) =>
      TypeMapping(id, typeUri)
    // Complex Mapping
    case _ => complexMapping
  }

  private def isPattern(inputs: Seq[Input]) = {
    inputs.forall{
      case PathInput(id, path) => true
      case TransformInput(id, ConstantTransformer(constant), Nil) => true
      case _ => false
    }
  }

  private def buildPattern(inputs: Seq[Input]) = {
    inputs.map {
      case PathInput(id, path) => "{" + path.serializeSimplified() + "}"
      case TransformInput(id, ConstantTransformer(constant), Nil) => constant
    }.mkString("")
  }
}
