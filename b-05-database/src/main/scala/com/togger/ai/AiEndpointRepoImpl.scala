package com.togger.ai

import com.togger.ai.AiEndpointRepoImpl.AI_ENDPOINTS_TABLE_FRAG
import com.togger.db.Util._
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.util.fragment.Fragment

class AiEndpointRepoImpl extends AiEndpointRepo with TgLogging {

  override def insert(row: AiEndpointRow): ConnectionIO[AiEndpointRow] = {
    import row._
    logAroundInfoQuery(
      insertStatement(AI_ENDPOINTS_TABLE_FRAG)(fr"(id, name) values ($id, $name)").update
        .withUniqueGeneratedKeys[AiEndpointRow]("id", "name")
    )(s"Inserting ${sp(row)}", saved => s"AI endpoint inserted ${sp(saved)}")
  }

  override def findAll: ConnectionIO[List[AiEndpointRow]] =
    logAroundInfoQuery(
      (selectAllStatement(AI_ENDPOINTS_TABLE_FRAG) ++ fr"order by id").query[AiEndpointRow].to[List]
    )("Finding all AI endpoints", found => s"Found ${found.size} AI endpoints")
}

object AiEndpointRepoImpl {
  val AI_ENDPOINTS_TABLE: String = "ai_endpoints"
  val AI_ENDPOINTS_TABLE_FRAG: Fragment = Fragment.const(AI_ENDPOINTS_TABLE)
}
