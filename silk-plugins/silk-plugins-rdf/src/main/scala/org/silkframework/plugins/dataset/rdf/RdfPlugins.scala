package org.silkframework.plugins.dataset.rdf

import org.silkframework.plugins.dataset.rdf.vocab.{RdfFilesVocabularyManager, RdfVocabularyManager}
import org.silkframework.runtime.plugin.PluginModule

class RdfPlugins extends PluginModule {

  override def pluginClasses =
    Seq(
      classOf[FileDataset],
      classOf[SparqlDataset],
      classOf[AlignmentDataset],
      classOf[InMemoryDataset],
      classOf[RdfVocabularyManager],
      classOf[RdfFilesVocabularyManager]
    )

}
