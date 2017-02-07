package controllers.transform

import play.api.mvc.{Action, Controller}

class TransformDialogs extends Controller {

  def transformationTaskDialog(projectName: String, taskName: String) = Action {
    Ok(views.html.dialogs.transformationTaskDialog(projectName, taskName))
  }

  def deleteRuleDialog(ruleName: String) = Action {
    Ok(views.html.dialogs.deleteRuleDialog(ruleName))
  }

}