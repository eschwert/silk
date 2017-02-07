/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.silkframework.plugins.dataset.rdf.sparql

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.logging.{Level, Logger}

import org.silkframework.dataset.rdf.{RdfNode, Resource, SparqlEndpoint}
import org.silkframework.entity.rdf.SparqlEntitySchema
import org.silkframework.entity.{Entity, EntitySchema, Path}
import org.silkframework.util.Uri

/**
 * EntityRetriever which executes multiple SPARQL queries (one for each property path) in parallel and merges the results into single entities.
 */
class ParallelEntityRetriever(endpoint: SparqlEndpoint, pageSize: Int = 1000, graphUri: Option[String] = None, useOrderBy: Boolean = false) extends EntityRetriever {
  private val varPrefix = "v"

  private val maxQueueSize = 1000

  private val logger = Logger.getLogger(classOf[ParallelEntityRetriever].getName)

  @volatile private var canceled = false

  /**
   * Retrieves entities with a given entity description.
   *
   * @param entitySchema The entity description
   * @param entities The URIs of the entities to be retrieved. If empty, all entities will be retrieved.
   * @return The retrieved entities
   */
  override def retrieve(entitySchema: EntitySchema, entities: Seq[Uri], limit: Option[Int]): Traversable[Entity] = {
    canceled = false
    if(entitySchema.typedPaths.size <= 1)
      new SimpleEntityRetriever(endpoint, pageSize, graphUri, useOrderBy).retrieve(entitySchema, entities, limit)
    else
      new EntityTraversable(entitySchema, entities, limit)
  }

  /**
   * Wraps a Traversable of SPARQL results and retrieves entities from them.
   */
  private class EntityTraversable(entitySchema: EntitySchema, entityUris: Seq[Uri], limit: Option[Int]) extends Traversable[Entity] {
    override def foreach[U](f: Entity => U) {
      var inconsistentOrder = false
      var counter = 0

      val pathRetrievers = for (path <- entitySchema.typedPaths) yield new PathRetriever(entityUris, SparqlEntitySchema.fromSchema(entitySchema), path.path)

      pathRetrievers.foreach(_.start())

      try {
        while (pathRetrievers.forall(_.hasNext) && !inconsistentOrder && limit.forall(counter <= _)) {
          val pathValues = for (pathRetriever <- pathRetrievers) yield pathRetriever.next()

          val uri = pathValues.head.uri
          if (pathValues.tail.forall(_.uri == uri)) {
            f(new Entity(uri, pathValues.map(_.values).toIndexedSeq, entitySchema))
            counter += 1
          }
          else {
            inconsistentOrder = true
            canceled = true
          }
        }
      }
      catch {
        case ex: InterruptedException => {
          logger.log(Level.INFO, "Canceled retrieving entities for '" + entitySchema.typeUri + "'")
          canceled = true
        }
        case ex: Exception => {
          logger.log(Level.WARNING, "Failed to execute query for '" + entitySchema.typeUri + "'", ex)
          canceled = true
        }
      }

      if (inconsistentOrder) {
        if (!useOrderBy) {
          logger.info("Querying endpoint '" + endpoint + "' without order-by failed. Using order-by.")
          val entityRetriever = new ParallelEntityRetriever(endpoint, pageSize, graphUri, true)
          val entities = entityRetriever.retrieve(entitySchema, entityUris, limit)
          entities.drop(counter).foreach(f)
        }
        else {
          logger.warning("Cannot execute queries in parallel on '" + endpoint + "' because the endpoint returned the results in different orders even when using order-by. Falling back to serial querying.")
          val simpleEntityRetriever = new SimpleEntityRetriever(endpoint, pageSize, graphUri)
          val entities = simpleEntityRetriever.retrieve(entitySchema, entityUris, limit)
          entities.drop(counter).foreach(f)
        }
      }
    }
  }

  private class PathRetriever(entityUris: Seq[Uri], entityDesc: SparqlEntitySchema, path: Path) extends Thread {
    private val queue = new ConcurrentLinkedQueue[PathValues]()

    @volatile private var exception: Throwable = null

    def hasNext: Boolean = {
      //If the queue is empty, wait until an element has been read
      while (queue.isEmpty && isAlive) {
        Thread.sleep(100)
      }

      //Throw exceptions which occurred during querying
      if (exception != null) throw exception

      !queue.isEmpty
    }

    def next(): PathValues = {
      //Throw exceptions which occurred during querying
      if (exception != null) throw exception

      queue.remove()
    }

    override def run() {
      try {
        if (entityUris.isEmpty) {
          //Query for all entities
          val sparqlResults = queryPath()
          parseResults(sparqlResults.bindings)
        }
        else {
          //Query for a list of entities
          for (entityUri <- entityUris) {
            val sparqlResults = queryPath(Some(entityUri))
            parseResults(sparqlResults.bindings, Some(entityUri))
          }
        }
      }
      catch {
        case ex: Throwable => exception = ex
      }
    }

    private def queryPath(fixedSubject: Option[Uri] = None) = {
      //Select
      var sparql = "SELECT DISTINCT "
      if (fixedSubject.isEmpty) {
        sparql += "?" + entityDesc.variable + " "
      }
      sparql += "?" + varPrefix + "0\n"

      //Body
      sparql += "WHERE {\n"
      //Graph
      for (graph <- graphUri if !graph.isEmpty) sparql += "GRAPH <" + graph + "> {\n"

      fixedSubject match {
        case Some(subjectUri) => {
          sparql += SparqlPathBuilder(path :: Nil, "<" + subjectUri + ">", "?" + varPrefix)
        }
        case None => {
          if (entityDesc.restrictions.toSparql.isEmpty)
            sparql += "?" + entityDesc.variable + " ?" + varPrefix + "_p ?" + varPrefix + "_o .\n"
          else
            sparql += entityDesc.restrictions.toSparql + "\n"
          sparql += SparqlPathBuilder(path :: Nil, "?" + entityDesc.variable, "?" + varPrefix)
        }
      }
      for (graph <- graphUri if !graph.isEmpty) sparql += "}\n"
      sparql += "}" // END WHERE

      if (useOrderBy && fixedSubject.isEmpty) {
        sparql += " ORDER BY " + "?" + entityDesc.variable
      }

      endpoint.select(sparql)
    }

    private def parseResults(sparqlResults: Traversable[Map[String, RdfNode]], fixedSubject: Option[Uri] = None) {
      var currentSubject: Option[String] = fixedSubject.map(_.uri)
      var currentValues: Seq[String] = Seq.empty

      for (result <- sparqlResults) {
        if (canceled) {
          return
        }

        if (fixedSubject.isEmpty) {
          //Check if we are still reading values for the current subject
          val subject = result.get(entityDesc.variable) match {
            case Some(Resource(value)) => Some(value)
            case _ => None
          }

          if (currentSubject.isEmpty) {
            currentSubject = subject
          } else if (subject.isDefined && subject != currentSubject) {
            while (queue.size > maxQueueSize && !canceled) {
              Thread.sleep(100)
            }

            queue.add(PathValues(currentSubject.get, currentValues))

            currentSubject = subject
            currentValues = Seq.empty
          }
        }

        if (currentSubject.isDefined) {
          for (node <- result.get(varPrefix + "0")) {
            currentValues = currentValues :+ node.value
          }
        }
      }

      for (s <- currentSubject if sparqlResults.nonEmpty) {
        queue.add(PathValues(s, currentValues))
      }
    }
  }

  private case class PathValues(uri: String, values: Seq[String])

}
