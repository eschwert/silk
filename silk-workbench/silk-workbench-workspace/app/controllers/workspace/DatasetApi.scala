package controllers.workspace

import controllers.util.SerializationUtils
import controllers.util.SerializationUtils._
import models.JsonError
import org.silkframework.dataset.rdf.{RdfDataset, SparqlResults}
import org.silkframework.dataset.{Dataset, DatasetPluginAutoConfigurable, DatasetTask}
import org.silkframework.entity.EntitySchema
import org.silkframework.runtime.serialization.{ReadContext, WriteContext}
import org.silkframework.workspace.User
import org.silkframework.workspace.activity.dataset.TypesCache
import play.api.libs.json.{JsArray, JsString}
import play.api.mvc.{Action, Controller}
import plugins.Context

class DatasetApi extends Controller {

  def getDataset(projectName: String, sourceName: String) = Action { implicit request =>
    implicit val project = User().workspace.project(projectName)
    val task = project.task[Dataset](sourceName)
    serialize(new DatasetTask(task.id, task.data))
  }

  def getDatasetAutoConfigured(projectName: String, sourceName: String) = Action { implicit request =>
    implicit val project = User().workspace.project(projectName)
    val task = project.task[Dataset](sourceName)
    val datasetPlugin = task.data
    datasetPlugin match {
      case autoConfigurable: DatasetPluginAutoConfigurable[_] =>
        val autoConfDataset = autoConfigurable.autoConfigured
        serialize(new DatasetTask(task.id, autoConfDataset))
      case _ =>
        NotImplemented(JsonError("The dataset type does not support auto-configuration."))
    }
  }

  def putDataset(projectName: String, sourceName: String, autoConfigure: Boolean) = Action { implicit request => {
    implicit val project = User().workspace.project(projectName)
    try {
      deserialize() { dataset: DatasetTask =>
        if(autoConfigure) {
          dataset.plugin match {
            case autoConfigurable: DatasetPluginAutoConfigurable[_] =>
              project.updateTask(dataset.id, autoConfigurable.autoConfigured.asInstanceOf[Dataset])
              Ok
            case _ =>
              NotImplemented(JsonError("The dataset type does not support auto-configuration."))
          }
        } else {
          project.updateTask(dataset.id, dataset.data)
          Ok
        }
      }
    } catch {
      case ex: Exception => BadRequest(JsonError(ex))
    }
  }}

  def deleteDataset(project: String, source: String) = Action {
    User().workspace.project(project).removeTask[Dataset](source)
    Ok
  }

  def datasetDialog(projectName: String, datasetName: String, title: String = "Edit Dataset") = Action { request =>
    val project = User().workspace.project(projectName)
    val datasetPlugin = if(datasetName.isEmpty) None else project.taskOption[Dataset](datasetName).map(_.data)
    Ok(views.html.workspace.dataset.datasetDialog(project, datasetName, datasetPlugin, title))
  }

  def datasetDialogAutoConfigured(projectName: String, datasetName: String, pluginId: String) = Action { request =>
    val project = User().workspace.project(projectName)
    implicit val prefixes = project.config.prefixes
    implicit val resources = project.resources
    val datasetParams = request.queryString.mapValues(_.head)
    val datasetPlugin = Dataset.apply(pluginId, datasetParams)
    datasetPlugin match {
      case ds: DatasetPluginAutoConfigurable[_] =>
        Ok(views.html.workspace.dataset.datasetDialog(project, datasetName, Some(ds.autoConfigured)))
      case _ =>
        NotImplemented("This dataset plugin does not support auto-configuration.")
    }
  }

  def dataset(project: String, task: String) = Action { implicit request =>
    val context = Context.get[Dataset](project, task, request.path)
    Ok(views.html.workspace.dataset.dataset(context))
  }

  def table(project: String, task: String, maxEntities: Int) = Action { implicit request =>
    val context = Context.get[Dataset](project, task, request.path)
    val source = context.task.data.source

    val firstTypes = source.retrieveTypes().head._1
    val paths = source.retrievePaths(firstTypes).toIndexedSeq
    val entityDesc = EntitySchema(firstTypes, paths.map(_.asStringTypedPath))
    val entities = source.retrieve(entityDesc).take(maxEntities).toList

    Ok(views.html.workspace.dataset.table(context, paths, entities))
  }

  def sparql(project: String, task: String, query: String = "") = Action { implicit request =>
    val context = Context.get[Dataset](project, task, request.path)

    context.task.data match {
      case rdf: RdfDataset =>
        val sparqlEndpoint = rdf.sparqlEndpoint
        var queryResults: Option[SparqlResults] = None
        if(!query.isEmpty) {
          queryResults = Some(sparqlEndpoint.select(query))
        }
        Ok(views.html.workspace.dataset.sparql(context, sparqlEndpoint, query, queryResults))
      case _ => BadRequest("This is not an RDF-Dataset.")
    }
  }

  /** Get types of a dataset including the search string */
  def types(project: String, task: String, search: String = "") = Action { request =>
    val context = Context.get[Dataset](project, task, request.path)
    val prefixes = context.project.config.prefixes

    val typesFull = context.task.activity[TypesCache].value.types
    val typesResolved = typesFull.map(prefixes.shorten)
    val allTypes = (typesResolved ++ typesFull).distinct
    val filteredTypes = allTypes.filter(_.contains(search))

    Ok(JsArray(filteredTypes.map(JsString)))
  }

  /** Get all types of the dataset */
  def getDatasetTypes(project: String, task: String) = Action { request =>
    val context = Context.get[Dataset](project, task, request.path)
    val types = context.task.activity[TypesCache].value.types

    Ok(JsArray(types.map(JsString)))
  }
}