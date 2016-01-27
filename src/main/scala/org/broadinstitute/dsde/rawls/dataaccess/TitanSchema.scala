package org.broadinstitute.dsde.rawls.dataaccess

import com.thinkaurelius.titan.core.Cardinality
import com.thinkaurelius.titan.core.schema.DefaultSchemaMaker

object TitanSchema {
  val INSTANCE = new TitanSchema()
}

class TitanSchema extends DefaultSchemaMaker {
  def defaultPropertyCardinality(key: String): Cardinality = Cardinality.LIST
  def ignoreUndefinedQueryTypes = true
}
