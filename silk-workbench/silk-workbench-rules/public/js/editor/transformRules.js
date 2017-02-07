var currentRule;
var confirmOnExit = false;
var modificationTimer;

$(function() {
  // Make rules sortable
  $("#ruleTable table").sortable({
    items: "> tbody"
  });
  //$("#typeContainer").sortable();

  // Initialize deletion dialog
  $("#dialogDelete").dialog({
    autoOpen: false,
    modal: true,
    buttons: {
      Yes: function() {
        currentRule.remove();
        modified();
        $(this).dialog("close");
      },
      Cancel: function() {
        $(this).dialog("close");
      }
    }
  });

  // Listen to modifications
  $(document).on('input', "input", function() {
    modified();
  });

  $("#ruleContainer").on("sortupdate", function( event, ui ) { modified() } );

  var categoryDictionary = {
    "MatchingCandidateCache": "Suggestions" ,
    "VocabularyCache": "Vocabulary Matches"
  }

  // define custom target property autocomplete widget:
  $.widget( "custom.catcomplete", $.ui.autocomplete, {
    _create: function() {
      this._super();
      this.widget().menu( "option", "items", "> :not(.ui-autocomplete-category)" );
    },
    _renderMenu: function( ul, items ) {
      var that = this,
        currentCategory = "";
      $.each( items, function( index, item ) {
        var li;
        if ( item.category != currentCategory ) {
          ul.append( "<li class='ui-autocomplete-category'>" + translateTerm(item.category, categoryDictionary) + "</li>" );
          currentCategory = item.category;
        }
        li = that._renderItemData( ul, item );
        if ( item.category ) {
          li.attr( "aria-label", item.category + " : " + item.label );
        }
      });
    },
    _renderItem: function( ul, item ) {
      var label = item.label ? item.label : getLocalName(item.value);
      if ( item.isCompletion ) {
        return $( "<li>" )
          .append( "<div><span class='ui-autocomplete-property-label'>" + label + "</span><br><span class='ui-autocomplete-property-uri'>" + item.value + "</span></div>" )
          .appendTo( ul );
      } else {
        return $("<li class='ui-autocomplete-warning'>").
          append( "<div>" + item.label + "</div>").
          prop("disabled", true).
          appendTo( ul );
      }

    }
  });

  // Add autocompletion
  addSourceAutocomplete($(".source"));
  addTargetAutocomplete($(".target"));

  // toggle URI mapping UI
  uriMappingExists() ? showURIMapping(true) : showURIMapping(false);

});

function getLocalName(uri) {
  if (uri) {
    var localNameDelimiterPattern = /[\/#:]/;
    return uri.split(localNameDelimiterPattern).pop();
  } else {
    return "(no URI defined)";
  }
}

function translateTerm(term, dictionary) {
  if (term in dictionary) {
    return dictionary[term];
  } else {
    return term;
  }
}

function modified() {
  confirmOnExit = true;
  showPendingIcon();
  clearTimeout(modificationTimer);
  modificationTimer = setTimeout(function() { save(); }, 2000);
}

function save() {
  clearTimeout(modificationTimer);

  // Check if rule names are unique
  // TODO set id and implement highlightElement
  var names = $("#ruleContainer").find(".name").map(function() { return $(this).val() } ).toArray();
  var duplicateNames = $.grep(names, function(v, i) { return $.inArray(v, names) != i });
  if(duplicateNames.length > 0) {
    var errors = duplicateNames.map(function(name) { return { type: "Error", message: "The following name is not unique: " + name }; } );
    updateStatus(errors);
    return;
  }

  // Commit rules
  $.ajax({
    type: 'PUT',
    url: apiUrl + '/rules',
    contentType: 'text/xml',
    processData: false,
    data: serializeRules(),
    success: function(response) {
      confirmOnExit = false;
      updateStatus([]);
    },
    error: function(req) {
      console.log('Error committing rule: ' + req.responseText);
      var errors = [ { type: "Error", message: req.responseText } ];
      updateStatus(errors);
    }
  });
}

function serializeRules() {
  var xmlDoc = $.parseXML('<TransformRules></TransformRules>');

  // Collect all rules
  $("#ruleContainer .transformRule").each(function() {
    // Read name
    var name = $(this).find(".rule-name").text();
    // Read source, target property and target type
    var source = $(this).find(".source").val();
    var target = $(this).find(".target").val();
    var nodeType = $(this).find(".rule-target-type select").val();
    if($(this).hasClass("directMapping")) {
      serializeDirectMapping(xmlDoc, name, source, target, nodeType);
    } else if($(this).hasClass("uriMapping")) {
      serializeUriMapping(xmlDoc, name, $(this).find(".pattern").val());
    } else if($(this).hasClass("objectMapping")) {
      serializeObjectMapping(xmlDoc, name, $(this).find(".pattern").val(), target);
    } else if($(this).hasClass("typeMapping")) {
      serializeTypeMapping(xmlDoc, name, $(this).find(".type").text());
    } else {
      var ruleXml = $.parseXML($(this).children('.ruleXML').text()).documentElement;
      serializeComplexRule(xmlDoc, ruleXml, name, target, nodeType);
    }
  });

  // Push to back-end
  var xmlString = (new XMLSerializer()).serializeToString(xmlDoc);
  return xmlString;
}

/**
 * Serializes a direct mapping.
 * A direct mapping is a 1-to-1 mapping between two properties
 */
function serializeDirectMapping(xmlDoc, name, source, target, nodeType) {
  // Create new rule
  var ruleXml = xmlDoc.createElement("TransformRule");
  ruleXml.setAttribute("name", name);

  // Add simple source
  var sourceXml = xmlDoc.createElement("Input");
  sourceXml.setAttribute("path", source);
  ruleXml.appendChild(sourceXml);

  // Add MappingTarget
  var mappingTarget = xmlDoc.createElement("MappingTarget");
  target = replacePrefix(target, prefixes);
  mappingTarget.setAttribute("uri", target);
  var valueType = xmlDoc.createElement("ValueType");
  valueType.setAttribute("nodeType", nodeType);
  mappingTarget.appendChild(valueType);
  ruleXml.appendChild(mappingTarget);

  // Add to document
  xmlDoc.documentElement.appendChild(ruleXml);
}

/**
 * Serializes a URI mapping.
 */
function serializeUriMapping(xmlDoc, name, pattern) {
  // Create new rule
  var ruleXml = xmlDoc.createElement("TransformRule");
  ruleXml.setAttribute("name", name);
  ruleXml.setAttribute("targetProperty", "");

  // Create concat transformer
  var concatXml = xmlDoc.createElement("TransformInput");
  concatXml.setAttribute("function", "concat");
  ruleXml.appendChild(concatXml);

  // Parse pattern
  var parts = pattern.split(/[\{\}]/);
  for (i = 0; i < parts.length; i++) {
    if (i % 2 == 0) {
      // Add constant
      var transformXml = xmlDoc.createElement("TransformInput");
      transformXml.setAttribute("function", "constant");
      var paramXml = xmlDoc.createElement("Param");
      paramXml.setAttribute("name", "value");
      paramXml.setAttribute("value", parts[i]);
      transformXml.appendChild(paramXml);
      concatXml.appendChild(transformXml);
    } else {
      // Add path
      var inputXml = xmlDoc.createElement("Input");
      inputXml.setAttribute("path", parts[i]);
      concatXml.appendChild(inputXml);
    }
  }

  // Add to document
  xmlDoc.documentElement.appendChild(ruleXml);
}

/**
 * Serializes a Object mapping.
 */
function serializeObjectMapping(xmlDoc, name, pattern, target) {
  // Create new rule
  var ruleXml = xmlDoc.createElement("TransformRule");
  ruleXml.setAttribute("name", name);
  ruleXml.setAttribute("targetProperty", target);

  // Create concat transformer
  var concatXml = xmlDoc.createElement("TransformInput");
  concatXml.setAttribute("function", "concat");
  ruleXml.appendChild(concatXml);

  // Parse pattern
  var parts = pattern.split(/[\{\}]/);
  for (i = 0; i < parts.length; i++) {
    if (i % 2 == 0) {
      // Add constant
      var transformXml = xmlDoc.createElement("TransformInput");
      transformXml.setAttribute("function", "constant");
      var paramXml = xmlDoc.createElement("Param");
      paramXml.setAttribute("name", "value");
      paramXml.setAttribute("value", parts[i]);
      transformXml.appendChild(paramXml);
      concatXml.appendChild(transformXml);
    } else {
      // Add path
      var inputXml = xmlDoc.createElement("Input");
      inputXml.setAttribute("path", parts[i]);
      concatXml.appendChild(inputXml);
    }
  }

  // Add MappingTarget
  var mappingTarget = xmlDoc.createElement("MappingTarget");
  target = replacePrefix(target, prefixes);
  mappingTarget.setAttribute("uri", target);
  var valueType = xmlDoc.createElement("ValueType");
  // The nodeType of object mappings is always Resource (UriValueType):
  valueType.setAttribute("nodeType", "UriValueType");
  mappingTarget.appendChild(valueType);
  ruleXml.appendChild(mappingTarget);

  // Add to document
  xmlDoc.documentElement.appendChild(ruleXml);
}

/**
 * Serializes a type mapping.
 */
function serializeTypeMapping(xmlDoc, name, type) {
  // Create new rule
  var ruleXml = xmlDoc.createElement("TransformRule");
  ruleXml.setAttribute("name", name);
  ruleXml.setAttribute("targetProperty", "http://www.w3.org/1999/02/22-rdf-syntax-ns#type");

  // Input is the constant type URI
  var transformXml = xmlDoc.createElement("TransformInput");
  transformXml.setAttribute("function", "constantUri");

  var paramXml = xmlDoc.createElement("Param");
  paramXml.setAttribute("name", "value");
  paramXml.setAttribute("value", type);

  transformXml.appendChild(paramXml);
  ruleXml.appendChild(transformXml);

  // Add to document
  xmlDoc.documentElement.appendChild(ruleXml);
}

/**
 * Serializes a complex rule.
 * For complex rules the rule contents are left untouched.
 */
function serializeComplexRule(xmlDoc, ruleXml, name, target, nodeType) {
  // Update name
  ruleXml.setAttribute("name", name);

  var mappingTarget = ruleXml.getElementsByTagName("MappingTarget")[0];
  var valueType = mappingTarget.getElementsByTagName("ValueType")[0];
  valueType.setAttribute("nodeType", nodeType);
  mappingTarget.appendChild(valueType);
  target = replacePrefix(target, prefixes);
  mappingTarget.setAttribute("uri", target);

  // Add to document
  xmlDoc.importNode(ruleXml, true);

//  console.log(ruleXml);
  xmlDoc.documentElement.appendChild(ruleXml);
}

/**
 * For curie, replace a prefix a with a full namespace and
 * return the resulting full URI.
 * Prefixes are provided as an object with { prefix: namespace }.
 */
function replacePrefix(curie, prefixes) {
  var curie_pattern = /^([a-z|_]+)\:.*?/;
  var match = null;
  if (match = curie.match(curie_pattern)) {
    var namespace = null;
    if (namespace = prefixes[match[1]]) {
      return curie.replace(match[0], namespace);
    } else {
      return curie;
    }
  } else {
    return curie;
  }
}

function addURIMapping() {
  addRule("#uriMappingTemplate");
  $(".uri-ui").toggle();
}

function addRule(template) {

  if (template == "#typeTemplate") {
    var typeTextfield = $("#rule-type-textfield");
    var typeInput = $("#rule-type-textfield input");
    var newRule = $(template + " .typeMapping").clone();
    var nameInput = newRule.find(".rule-name");
    var ruleName = generateRuleName(nameInput.text());
    nameInput.text(ruleName);
    var ruleId = "type-" + ruleName;
    newRule.attr("id", ruleId);
    var typeString = typeInput.val();
    newRule.find(".type").text(typeString);
    newRule.appendTo("#typeContainer");
    typeInput.val("");
    typeTextfield.removeClass("is-dirty");
    var deleteButton = newRule.find("button");
    deleteButton.attr("onclick", "deleteRule('" + ruleId + "');");
  } else if(template == "#uriMappingTemplate") {
    var newRule = $(template).children().clone();
    resetMDLTextfields(newRule);
    newRule.appendTo(".uri-ui--defined");
  } else {
    // Clone rule template
    var newRule = $(template).children().clone();

    var nameInput = newRule.find(".rule-name");
    var oldRuleName = nameInput.text();
    var newRuleName = generateRuleName(oldRuleName);
    nameInput.text(newRuleName);

    var ruleRows = newRule.find("tr");
    $.each(ruleRows, function(index, row) {
      row = $(row);
      var ruleId = row.attr("id");
      ruleId = ruleId.replace(oldRuleName, newRuleName);
      row.attr("id", ruleId);
      row.find("button.delete-button").attr("onclick", "deleteRule('" + ruleId + "');");
    })

    resetMDLTextfields(newRule);

    newRule.appendTo("#ruleTable table");
    $(".mdl-layout__content").animate({
      scrollTop: $("#content").height()
    }, 300);

    addTypeSelections(newRule.find('select'));
  }

  componentHandler.upgradeAllRegistered();

  // Add autocompletion
  addSourceAutocomplete(newRule.find(".source"));
  addTargetAutocomplete(newRule.find(".target"));

  // Set modification flag
  modified();
}

function resetMDLTextfields(element) {
  // remove dynamic mdl classes and attributes
  // (otherwise componentHandler.upgradeAllRegistered() won't work)

  var textfields = element.find(".mdl-textfield");
  $.each(textfields, function(index, value) {
    value.removeAttribute("data-upgraded");
    var classes = value.className;
    var new_classes = classes.replace(/is-upgraded/, '').replace(/is-dirty/, '');
    value.className = new_classes;
  });
}

function deleteRule(node) {
  showDialog(baseUrl + '/transform/dialogs/deleteRule/' + encodeURIComponent(node));
}

function openRule(name) {
  clearTimeout(modificationTimer);
  $.ajax({
    type: 'PUT',
    url: apiUrl + '/rules',
    contentType: 'text/xml',
    processData: false,
    data: serializeRules(),
    success: function(response) {
      window.location.href = "./editor/" + name
    },
    error: function(req) {
      console.log('Error committing rule: ' + req.responseText);
      alert(req.responseText);
    }
  });
}

function generateRuleName(prefix) {
  var count = 0;
  do {
    count = count + 1;
    if($("#ruleContainer").find(".rule-name").filter(function() { return $(this).text() == prefix + count } ).length == 0) {
      return prefix + count;
    }
  } while (count < 1000);
}

function toggleRuleConfig() {
  var confContent = $("#ruleConfigContainer .mdl-card__supporting-text");
  var buttons = $("#ruleConfigContainer .mdl-card__title button");
  confContent.toggle(50, function() { buttons.toggle(); });
}

function toggleRule(ruleId) {
  var expandedRule = $("#" + ruleId + "__expanded");
  var buttons = $("#" + ruleId + " .rule-toggle button");
  expandedRule.toggle(50, function() { buttons.toggle(); });
}

function uriMappingExists() {
  return $(".uri-ui--defined").children()[0] != null;
}

function checkForEmptyURIMapping() {
  var pattern = $("#uri-pattern").val();
  if (pattern.match(/^\s*$/)) { // if empty or only whitespace
    $('#uri').remove();
    showURIMapping(false);
    modified();
  }
}

function showURIMapping(defined) {
  if (defined) {
    $(".uri-ui--defined").show();
    $(".uri-ui--replacement").hide();
  } else {
    $(".uri-ui--defined").hide();
    $(".uri-ui--replacement").show();
  }
}

function addTypeAutocomplete(typeInputs) {
  typeInputs.autocomplete({
    source: apiUrl + "/targetPathCompletions" ,
    minLength: 0 ,
    select: function(event, ui) {
      window.setTimeout(function() { $("#rule-type-textfield input").trigger("enter"); }, 5);
    }
  }).focus(function() { $(this).autocomplete("search"); });
}

function addSourceAutocomplete(sourceInputs) {
  sourceInputs.autocomplete({
    source: apiUrl + "/sourcePathCompletions" ,
    minLength: 0 ,
    position: { my: "left bottom", at: "left top", collision: "flip" } ,
    close: function(event, ui) { modified(); }
  }).focus(function() { $(this).autocomplete("search"); });
}

function addTargetAutocomplete(targetInputs) {
  targetInputs.each(function() {
    var sourceInput = $(this).closest("tr").find(".source");
    var patternInput = $(this).closest("tr").find(".pattern");
    $(this).catcomplete({
      source: function( request, response ) {
        if(sourceInput.length > 0) {
          // We got a mapping that specifies a source property
          request.sourcePath = sourceInput.val();
        } else if(patternInput.length > 0) {
          // We got a mapping that specifies a URI pattern of the form http://example.org/{ID}
          // We try to take the first path inside the parentheses.
          // If this fails, the endpoint will still suggest properties from the vocabulary
          request.sourcePath = patternInput.val().split(/[\{\}]/)[1];
        }

        $.getJSON( apiUrl + "/targetPathCompletions", request, function(data) { response( data ) });
      },
      minLength: 0,
      position: { my: "left bottom", at: "left top", collision: "flip" } ,
      close: function(event, ui) { modified(); } ,
      focus: function(event, ui) { changePropertyDetails(ui.item.value, $(this));}
    }).focus(function() { $(this).catcomplete("search"); });

    // Update the property details on every change
    changePropertyDetails($(this).val(), $(this));
    $(this).keyup(function() {
      changePropertyDetails($(this).val(), $(this));
    });
  });
}

function addTypeSelections(typeSelects) {

  var types = [
    { label: "Autodetect", value: "AutoDetectValueType", category: "" } ,
    { label: "Resource", value: "UriValueType", category: "" } ,
    { label: "Boolean", value: "BooleanValueType", category: "Literals" } ,
    { label: "String", value: "StringValueType", category: "Literals" } ,
    { label: "Integer", value: "IntegerValueType", category: "Literals (Numbers)" } ,
    { label: "Long", value: "LongValueType", category: "Literals (Numbers)" } ,
    { label: "Float", value: "FloatValueType", category: "Literals (Numbers)" } ,
    { label: "Double", value: "DoubleValueType", category: "Literals (Numbers)" } ,
  ];

  // fill the select lists
  var currentCategory = "";
  var target = typeSelects;
  $.each(types, function(index, value) {
    if ( value.category != currentCategory ) {
      currentCategory = value.category;
      typeSelects.append("<optgroup label='" + currentCategory + "'/>");
      target = typeSelects.find("optgroup:last-child");
    }
    target.append("<option value='" + value.value + "'>" + value.label + "</option>");
  });

  // select correct element
  $.each(typeSelects, function(index, value) {
    var targetType = $(value).data('originalTargetType');
    $(value).val(targetType);
  });

  // register changes
  typeSelects.change(function() {
    modified();
  });

}

function changePropertyDetails(propertyName, element) {
  var details = element.closest(".complete-rule").find(".di-rule__expanded-property-details");
  $.get(editorUrl + '/widgets/property', { property: propertyName }, function(data) { details.html(data); });
}
