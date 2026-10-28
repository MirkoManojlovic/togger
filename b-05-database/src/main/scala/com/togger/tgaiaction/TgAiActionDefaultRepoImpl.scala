package com.togger.tgaiaction

import com.togger.db.Util._
import com.togger.logging.TgLogging
import com.togger.tgaiaction.TgAiActionDefaultRepoImpl.TG_AI_ACTION_DEFAULT_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.util.UUID

class TgAiActionDefaultRepoImpl extends TgAiActionDefaultRepo with TgLogging {

  override def upsert(default: TgAiActionDefault): ConnectionIO[TgAiActionDefault] = {
    import default._
    logAroundInfoQuery(
      insertStatement(TG_AI_ACTION_DEFAULT_TABLE_FRAG)(fr"""
        (account_id, project_id, ai_endpoint_id, tg_ai_action_id)
        values ($accountId, $projectId, $aiEndpointId, $tgAiActionId)
        on conflict (account_id, project_id, ai_endpoint_id) do update set tg_ai_action_id = excluded.tg_ai_action_id
      """).update.withUniqueGeneratedKeys[TgAiActionDefault]("account_id", "project_id", "ai_endpoint_id", "tg_ai_action_id")
    )(s"Upserting ${sp(default)}", saved => s"AI action default saved ${sp(saved)}")
  }

  override def delete(accountId: UUID, projectId: UUID, endpointId: Int): ConnectionIO[Int] =
    logAroundInfoQuery(
      (fr"delete from" ++ TG_AI_ACTION_DEFAULT_TABLE_FRAG ++
        fr"where account_id = $accountId and project_id = $projectId and ai_endpoint_id = $endpointId").update.run
    )(
      s"Deleting AI action default of ${sp(accountId)} for ${sp(projectId)} and endpoint $endpointId",
      _ => "AI action default deleted"
    )

  override def find(accountId: UUID, projectId: UUID, endpointId: Int): ConnectionIO[Option[TgAiActionDefault]] =
    (selectAllStatement(TG_AI_ACTION_DEFAULT_TABLE_FRAG) ++
      fr"where account_id = $accountId and project_id = $projectId and ai_endpoint_id = $endpointId")
      .query[TgAiActionDefault]
      .option

  override def findByProject(accountId: UUID, projectId: UUID): ConnectionIO[List[TgAiActionDefault]] =
    logAroundInfoQuery(
      (selectAllStatement(TG_AI_ACTION_DEFAULT_TABLE_FRAG) ++
        fr"where account_id = $accountId and project_id = $projectId order by ai_endpoint_id")
        .query[TgAiActionDefault]
        .to[List]
    )(
      s"Finding AI action defaults of ${sp(accountId)} for ${sp(projectId)}",
      found => s"Found ${found.size} AI action defaults"
    )
}

object TgAiActionDefaultRepoImpl {
  val TG_AI_ACTION_DEFAULT_TABLE: String = "tg_ai_action_default"
  val TG_AI_ACTION_DEFAULT_TABLE_FRAG: Fragment = Fragment.const(TG_AI_ACTION_DEFAULT_TABLE)
}
